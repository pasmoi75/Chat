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

	public synchronized int getTimestamp() {
		return lamport_timestamp;
	}

	public synchronized void setTimestamp(int lamport_timestamp) {
		this.lamport_timestamp = lamport_timestamp;
	}

	@Override
	public void mainloop() {
		while (true) {

			boolean ok = true;

			while (ok) {
				Message mess = priority.peek();
				/*if(collection_ack.size() > 0)
					System.out.println(collection_ack);*/
				
				if (mess != null) {	
					//System.out.println("Message qui bloque tout : "+mess.getClass().getName()+" ID : "+mess.id_sender+" Timestamp : "+mess.timestamp);
					
					ByteBuffer payload_ack = ByteBuffer.allocate(8);
					payload_ack.putInt(mess.id_sender);
					payload_ack.putInt(mess.timestamp);
					payload_ack.flip();
					AckMessage related_ack = new AckMessage(mess.timestamp,mess.id_sender, payload_ack.array());

					Integer numb_ack = collection_ack.get(related_ack);
					//if(numb_ack != null)
						//System.out.println("Nombre de Ack reçus : "+numb_ack.intValue()+" Taille de la channel list :"+ channel_list.size());
					
					if(numb_ack != null && (numb_ack.intValue() == channel_list.size())){
						System.out.println(mess.getClass().getName()+" Delivered : ID = "+mess.id_sender+" and Timestamp = "+mess.timestamp);
						deliver.deliver(getChannelList().get(0),mess.sendMessage());
						mess = priority.poll();
					} else {
						ok = false ;
					}
				} else {
					ok = false;
				}
			}

			try {
				int keys_number = selector.select(500);
				if (keys_number > 0) {
				 //System.out.println("Timestamp : "+getTimestamp()+" Number of keys :" + keys_number);
					Set<SelectionKey> selectedKeys = selector.selectedKeys();
					Iterator<SelectionKey> iter = selectedKeys.iterator();

					while (iter.hasNext()) {

						SelectionKey ky = iter.next();
						if (ky.isValid())
//							System.out.println("Keys Ready Ops :" +
//							 ky.readyOps() + " InterestOps :" +
//							ky.interestOps());
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
							//ByteBuffer buffer = pair.getReceiveBuffer();
							ByteBuffer buffer = ByteBuffer.allocate(1 << 19);
							try {
								
								int bytesread = client.read(buffer);
								if (bytesread > 0) {
									buffer.flip();
									readCount += bytesread;
									// pair.handleMessage(buffer);
									System.out.println("Bytes read : "+bytesread);
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
								pair.mutex.lock();
								pair.getSendBuffer().flip();
								//System.out.println("Buffer position : "+pair.getSendBuffer().position()+" \nBuffer capacity :"+pair.getSendBuffer().capacity()+" \nBuffer Limit :"+pair.getSendBuffer().limit());
								int bytesWritten = client.write(pair
										.getSendBuffer());
								writeCount += bytesWritten;
								System.out.println("bytes Written : "+bytesWritten);
								pair.getSendBuffer().clear() ;
								pair.mutex.unlock();	
								pair.send(null, 0, 0); //Juste pour vider la file de messages
								
								if(bytesWritten != 0){
									ky.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
								} else {
									ky.interestOps(SelectionKey.OP_READ);
								}
								
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
			Integer numb_ack = collection_ack.get((AckMessage)m);
			Integer new_value_ack = numb_ack == null ? 1 : numb_ack + 1;
			collection_ack.put((AckMessage) m, new_value_ack);
		} else {
			
			priority.add(m);

		}
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
