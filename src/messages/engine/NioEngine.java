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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class NioEngine extends Engine {

	private List<Channel> channel_list;
	private Selector selector;
	private TreeMap<Message, Long> ordered_map;

	public NioEngine() throws IOException {
		selector = Selector.open();
		channel_list = new LinkedList<Channel>();
	}

	@Override
	public void mainloop() {
		while (true) {
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
							NioDeliver delivercallback = (NioDeliver) pair
									.getDeliverCallback();
							NioConnect connectcallback = (NioConnect) pair
									.getConnectcallback();

							ByteBuffer buffer = ByteBuffer.allocate(1 << 19);
							try {
								int bytesread = client.read(buffer);
								if (bytesread > 0) {
									buffer.flip();
									readCount += bytesread;
									pair.handleMessage(buffer);

								} else if (bytesread == -1) {
									System.out
											.println("EOF. Remote peer has closed the connection.");
									connectcallback.closed(pair);
								}
							} catch (IOException e) {
								e.printStackTrace();
								connectcallback.closed(pair);
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

	class Comparateur_Date implements Comparator {

		Map<Message, Long> tuple;

		public Comparateur_Date(TreeMap<Message, Long> map) {
			this.tuple = map;
		}

		// ce comparateur ordonne les éléments dans l'ordre décroissant
		@Override
		public int compare(Object o1, Object o2) {
			// TODO Auto-generated method stub
			if (tuple.get(o1) < (tuple.get(o2))) {
				return -1;
			} else {
				return 1;
			}
		}
	}

	public synchronized void addToMap(Message mess, Long ack) {

		if (ack == null) {
			
			if(ordered_map.containsValue(mess.date)) {
				
				TreeMap<Message, Long> new_map = new TreeMap<>();
				for (Message message : ordered_map.keySet()) {

					if (message.date == mess.date) {
						mess.nb_ack = message.nb_ack;
						new_map.put(mess, mess.date);
					}
					else
					new_map.put(message, message.date);

				}

				ordered_map = new_map;
				
			}
			
			else 
			ordered_map.put(mess, mess.date);
			
		} else {

			if (ordered_map.containsValue(ack)) {

				TreeMap<Message, Long> new_map = new TreeMap<>();
				for (Message message : ordered_map.keySet()) {

					if (message.date == ack) {
						message.nb_ack += 1;
					}

					new_map.put(message, message.date);

				}

				ordered_map = new_map;

			}
			
			else {
				Message nouveau = new Message();
				nouveau.date = ack;
				nouveau.nb_ack = 1;
				ordered_map.put(nouveau,ack);
			}
		}

		Comparateur_Date comp = new Comparateur_Date(ordered_map);
		TreeMap<Message, Long> map_triee = new TreeMap<Message, Long>(comp);
		map_triee.putAll(ordered_map);
		ordered_map = map_triee;
	}

}
