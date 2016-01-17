package messages.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class NioEngine extends Engine {

	private int id;
	private int lamport_timestamp = 0;
	private int listening_port;
	private List<Channel> channel_list;
	private Selector selector;
	private PriorityQueue<Message> priority;
	private Map<AckMessage, Integer> collection_ack;
	private MessageHandler handler = new MessageHandler(this);
	private Map<Integer, byte[]> peers_map;
	private NioDeliver deliver = new NioDeliver(this) ;


	public NioEngine() throws IOException {
		selector = Selector.open();
		channel_list = new LinkedList<Channel>();
		priority = new PriorityQueue<Message>(50);
		collection_ack = new HashMap<AckMessage, Integer>();
		peers_map = new TreeMap<Integer, byte[]>();
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getTimestamp() {
		return lamport_timestamp;
	}

	public void setTimestamp(int lamport_timestamp) {
		this.lamport_timestamp = lamport_timestamp;
	}

	@Override
	public void mainloop() {
		while (true) {

			boolean ok = true;

			while (ok) {
				System.out.println("TAILLE : " + collection_ack.size());

				Message mess = priority.peek();		
				if (mess != null && collection_ack.get(mess) == getChannel_list().size()) {
					System.out.println("BON SIGNE");
					deliver.deliver(getChannel_list().get(0),
							mess.sendMessage());
					mess = priority.poll();
				} else {
					ok = false;
				}
			}

			try {
				int keys_number = selector.select(500);
				if (keys_number > 0) {
					// System.out.println("Number of keys :" + keys_number);
					Set<SelectionKey> selectedKeys = selector.selectedKeys();
					Iterator<SelectionKey> iter = selectedKeys.iterator();

					while (iter.hasNext()) {

						SelectionKey ky = iter.next();
						if (ky.isValid())
							// System.out.println("Keys Ready Ops :" +
							// ky.readyOps() + " InterestOps :" +
							// ky.interestOps());
							if (ky.isValid() && ky.isAcceptable()) {
								SocketChannel client;
								try {
									ServerSocketChannel service = (ServerSocketChannel) ky
											.channel();
									client = service.accept();
									if (client != null) {
										client.configureBlocking(false);
										NioChannel client_new = new NioChannel(
												this, client);
										SelectionKey seleccion = client
												.register(selector,
														SelectionKey.OP_READ,
														client_new);
										client_new.setSelectionkey(seleccion);
										NioAccept acceptcallback = (NioAccept) ky
												.attachment();
										acceptcallback.accepted(
												acceptcallback.getServer(),
												client_new);
									}
								} catch (IOException e) {
									e.printStackTrace();
								}

							}
						if (ky.isValid() && ky.isConnectable()) {
							SocketChannel channel = (SocketChannel) ky
									.channel();
							if (channel.isConnectionPending()
									&& channel.finishConnect()) {
								NioConnect connectcallback = (NioConnect) ky
										.attachment();
								connectcallback.setSocketChannel(channel);
								connectcallback.connected(connectcallback
										.getChannel());
							}
						}

						if (ky.isValid() && ky.isReadable()) {
							SocketChannel client = (SocketChannel) ky.channel();

							NioChannel pair = (NioChannel) ky.attachment();
							NioConnect connectcallback = (NioConnect) pair
									.getConnectcallback();

							ByteBuffer buffer = ByteBuffer.allocate(1 << 19);
							try {
								int bytesread = client.read(buffer);
								if (bytesread > 0) {
									buffer.flip();
									readCount += bytesread;
									// pair.handleMessage(buffer);
									try {
										handler.handleMessage(buffer, pair);
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

								} else if (bytesread == -1) {
									System.out
											.println("EOF. Remote peer has closed the connection.");
									connectcallback.closed(pair);
								}
							} catch (IOException e) {
								e.printStackTrace();
								connectcallback.closed(pair);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						if (ky.isValid() && ky.isWritable()) {
							SocketChannel client = (SocketChannel) ky.channel();
							NioChannel pair = (NioChannel) ky.attachment();
							NioConnect connectcallback = (NioConnect) pair
									.getConnectcallback();

							try {
								pair.getSendBuffer().flip();
								int bytesWritten = client.write(pair
										.getSendBuffer());
								writeCount += bytesWritten;
								ky.interestOps(SelectionKey.OP_READ);
								// System.out.println("bytes Written : "+bytesWritten);
								if (!client.isOpen()) {
									connectcallback.closed(pair);
								}

							} catch (Exception e) {
								e.printStackTrace();
								connectcallback.closed(pair);
							}
						}
						iter.remove();
					}
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	@Override
	public Server listen(int port, AcceptCallback callback) throws IOException {
		Server serveur;

		ServerSocketChannel serverchannel = ServerSocketChannel.open();
		InetSocketAddress hostAddress = new InetSocketAddress("localhost", port);
		serverchannel.bind(hostAddress);
		listening_port = port;

		serveur = new ConcreteServer(serverchannel);
		System.out.println("Listening Incoming connections on Port :"
				+ serveur.getPort());

		/* Registering server */
		((ConcreteServer) serveur).getServerchannel().configureBlocking(false);
		((ConcreteServer) serveur).getServerchannel().register(selector,
				SelectionKey.OP_ACCEPT, callback);

		return serveur;

	}

	@Override
	public void connect(InetAddress hostAddress, int port,
			ConnectCallback callback) throws UnknownHostException,
			SecurityException, IOException {
		SocketChannel client = SocketChannel.open();
		client.configureBlocking(false);

		if (!client.connect(new InetSocketAddress(hostAddress, port))) {
			SelectionKey clientKey = client.register(selector,
					SelectionKey.OP_CONNECT, callback);
			((NioConnect) callback).setSelectionKey(clientKey);
		} else {
			SelectionKey clientKey = client.register(selector,
					SelectionKey.OP_READ, callback);
			((NioConnect) callback).setSocketChannel(client);
			callback.connected(((NioConnect) callback).getChannel());
		}

	}

	public synchronized List<Channel> getChannelList() {
		return channel_list;
	}

	public synchronized void addToMap2(Message m) {

		if (m instanceof AckMessage) {
			Integer numb_ack = collection_ack.get(m);
			Integer new_value_ack = numb_ack == null ? 1 : numb_ack + 1;
			collection_ack.put((AckMessage) m, new_value_ack);
		} else {
			
			priority.add(m);

		}
	}

	public synchronized List<Channel> getChannel_list() {
		return channel_list;

	}

	public synchronized Map<Integer, byte[]> getPeersMap() {
		return peers_map;
	}

	public byte[] getPeersList() {
		Set<Integer> peer_set = peers_map.keySet();
		Iterator<Integer> it = peer_set.iterator();

		/* 6 bytes for IPAddress (4+2) and 4 bytes for Peer_ID */
		ByteBuffer buffer_peers = ByteBuffer.allocate(peer_set.size() * 10);

		while (it.hasNext()) {
			Integer key = it.next();
			byte[] result_key = peers_map.get(key);

			buffer_peers.putInt(key.intValue());
			buffer_peers.put(result_key);
		}
		buffer_peers.flip();

		return buffer_peers.array();
	}

	public synchronized void setChannel_list(List<Channel> channel_list) {
		this.channel_list = channel_list;
	}

	public int getListeningPort() {
		return listening_port;
	}

}
