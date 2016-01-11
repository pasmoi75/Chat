package messages.engine;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class NioChannel extends Channel {

	private Engine engine;
	private SelectionKey selectionkey;
	private SocketChannel channel;
	private ByteBuffer rcv_buffer;
	private ByteBuffer send_buffer;
	private DeliverCallback delivercallback;
	private ConnectCallback connectcallback;
	private boolean nouveau_venu ;
	private Integer local_port;
	
	
	
	public class BufferState {
		public int messagelength;
		public int remaining;

		public BufferState(int length, int remain) {
			messagelength = length;
			remaining = remain;
		}
	}

	private BufferState bufferstate;

	/* Constructor for Incoming connections */
	public NioChannel(Engine engine, SocketChannel channel) {
		this.engine = engine;
		this.channel = channel;
		this.delivercallback = new NioDeliver(this);
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 8);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
		this.nouveau_venu=((NioEngine)engine).nouveau_venu;
	}

	/* Constructor for Outgoing connections */
	public NioChannel(Engine engine, InetAddress hostAddress, int port, int local_port)
			throws UnknownHostException, SecurityException, IOException {
		this.engine = engine;
		this.delivercallback = new NioDeliver(this);
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 19);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
		this.engine.connect(hostAddress, port, connectcallback);
		this.nouveau_venu=((NioEngine)engine).nouveau_venu;
		this.local_port=local_port;

	}

	public ConnectCallback getConnectcallback() {
		return connectcallback;
	}

	public ByteBuffer getReceiveBuffer() {
		return rcv_buffer;
	}

	public ByteBuffer getSendBuffer() {
		return send_buffer;
	}

	public void setChannel(SocketChannel channel) {
		this.channel = channel;
	}

	public SocketChannel getChannel() {
		return channel;
	}

	public SelectionKey getSelectionkey() {
		return selectionkey;
	}

	public void setSelectionkey(SelectionKey selectionkey) {
		this.selectionkey = selectionkey;
	}

	@Override
	public void setDeliverCallback(DeliverCallback callback) {
		this.delivercallback = callback;
	}

	public DeliverCallback getDeliverCallback() {
		return delivercallback;
	}

	// public void handleMessage(ByteBuffer buffer){
	// int length = buffer.getInt() ;
	//
	// if(length > buffer.remaining()){
	// bufferstate = new BufferState(length,length-buffer.remaining());
	// rcv_buffer.put(buffer); //Stores the partial message in a Buffer
	// rcv_buffer.position(rcv_buffer.position()-buffer.remaining());
	// } else {
	// byte[] deliver_array = new byte[length];
	// buffer.get(deliver_array, 0, length);
	// delivercallback.deliver(this,deliver_array);
	// }
	// if(buffer.hasRemaining()){
	// handleMessage(buffer);
	// }
	// }

	/* Method which handles and parses received messages */
	public void handleMessage(ByteBuffer buffer) {

		int length = buffer.getInt();
		int id = buffer.getInt();

		System.out.println("Longueur :" + length);
		System.out.println("Identifiant :" + id);

		System.out.println(channel);

		switch (id) {
		case 0:
			// reception simple de message
			if (length > buffer.remaining()) {
				bufferstate = new BufferState(length, length
						- buffer.remaining());
				rcv_buffer.put(buffer); // Stores the partial message in a
										// Buffer
				rcv_buffer.position(rcv_buffer.position() - buffer.remaining());
			} else {
				byte[] deliver_array = new byte[length];
				long date = buffer.getLong();
				buffer.get(deliver_array, 0, length);
				Message mess = new Message(date, deliver_array);
				engine.addToQueue(mess, null);
				String mot = String.valueOf(date);
				// on envoit le ack aux autres
				if(!nouveau_venu) 
				for (Channel channel : ((NioEngine) engine).getChannelList()) {
					channel.send(mot.getBytes(), 0, length, 1);
				}
				

			}
			if (buffer.hasRemaining()) {
				handleMessage(buffer);
			}

			break;
		case 1:
			// Reception d'un Ack
			Long timestamp = buffer.getLong();
			engine.addToQueue(null, timestamp);

			break;
		case 2:
			// reception d'une demande pour rejoindre le groupe

			for (Channel channel : ((NioEngine) engine).getChannelList()) {
				if (!channel.equals(this)&&!nouveau_venu) {
					byte[] deliver_array1 = new byte[length];
					buffer.get(deliver_array1, 0, length);

					channel.send(deliver_array1, 0, length, 3);
				}
			}

			break;
		case 3:
			// reception d'un nouveau venu
			if (length > buffer.remaining()) {
				bufferstate = new BufferState(length, length
						- buffer.remaining());
				rcv_buffer.put(buffer); // Stores the partial message in a
										// Buffer
				rcv_buffer.position(rcv_buffer.position() - buffer.remaining());
			} else {
				byte[] deliver_array1 = new byte[length];
				buffer.get(deliver_array1, 0, length);
				String ip_port = new String(deliver_array1);
				String ip = ip_port.substring(0, ip_port.indexOf(":"));
				String port = ip_port.substring(ip_port.indexOf(":") + 1);
				System.out.println(port);
				try {
					NioChannel new_channel = new NioChannel(this.engine,
							InetAddress.getByName("localhost"), Integer.valueOf(port), this.local_port);
				} catch (NumberFormatException | SecurityException
						| IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (buffer.hasRemaining()) {
				handleMessage(buffer);
			}

			break;
		}

	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
	}

	@Override
	public void send(byte[] bytes, int offset, int length, int type) {
		// System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
//		if(((NioEngine)engine).getChannel_list().size()%3==0)
//			send_buffer.clear();
		if (send_buffer.capacity() - send_buffer.position() > length + 4) {
			send_buffer.putInt(length);
			send_buffer.putInt(type);
			if (bytes != null)
				send_buffer.put(bytes, offset, length);

			selectionkey.interestOps(SelectionKey.OP_READ
					| SelectionKey.OP_WRITE);
		} else {
			System.out.println("Send Buffer is Full");
		}

	}
	
	public void sendmessage(byte[] bytes, int offset, int length, long date) {
		// System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
//		if(((NioEngine)engine).getChannel_list().size()%3==0)
//			send_buffer.clear();
		if (send_buffer.capacity() - send_buffer.position() > length + 8) {
			send_buffer.putInt(length);
			send_buffer.putInt(0);
			send_buffer.putLong(date);
			if (bytes != null)
				send_buffer.put(bytes, offset, length);

			selectionkey.interestOps(SelectionKey.OP_READ
					| SelectionKey.OP_WRITE);
		} else {
			System.out.println("Send Buffer is Full");
		}

	}

	@Override
	public void close() {
		try {
			selectionkey.cancel();
			channel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String bytetoIPv4(byte[] peers) {
		String result = "\n\t\t";
		try {
			int i;
			for (i = 0; i <= peers.length - 6; i = i + 6) {
				for (int j = i; j < i + 5; j++) {
					if (j < i + 3) {
						result += (256 + peers[j]) % 256 + ".";
					} else if (j < i + 4) {
						result += (256 + peers[j]) % 256;
					} else {
						int port_number = (((256 + peers[j]) % 256) << 8)
								+ (256 + peers[j + 1]) % 256;
						result += ":" + port_number + "\n\t\t";
					}
				}
			}

		} catch (Exception n) {
			n.printStackTrace();
		}
		return result;
	}

	public boolean isNouveau_venu() {
		return nouveau_venu;
	}

	public void setNouveau_venu(boolean nouveau_venu) {
		this.nouveau_venu = nouveau_venu;
	}

	public Integer getLocal_port() {
		return local_port;
	}

	public void setLocal_port(Integer local_port) {
		this.local_port = local_port;
	}

	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		byte[] sortie = new byte[4];
		buffer.putLong(x);
		buffer.get(sortie, 0, 4);
		return sortie;
	}

	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}

}
