package messages.engine;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class NioChannel extends Channel {

	private Engine engine;
	private SelectionKey selectionkey;
	private SocketChannel channel;
	private ByteBuffer rcv_buffer;
	private ByteBuffer send_buffer;
	private DeliverCallback delivercallback;
	private ConnectCallback connectcallback;
	private boolean nouveau_venu;
	private int unreadposition ;
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
		this.nouveau_venu = ((NioEngine) engine).nouveau_venu;
	}

	/* Constructor for Outgoing connections */
	public NioChannel(Engine engine, InetAddress hostAddress, int port,
			int local_port) throws UnknownHostException, SecurityException,
			IOException {
		this.engine = engine;
		this.delivercallback = new NioDeliver(this);
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 19);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
		this.engine.connect(hostAddress, port, connectcallback);
		this.nouveau_venu = ((NioEngine) engine).nouveau_venu;
		this.local_port = local_port;

	}
	
	public Engine getEngine(){
		return engine ;
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
		byte id = buffer.get();

		System.out.println("Longueur :" + length);
		System.out.println("Message Type :" + id);

		switch (id) {
		case 0:
			// reception simple de message
			if (length > buffer.remaining()) {
				bufferstate = new BufferState(length, length
						- buffer.remaining());
				rcv_buffer.put(buffer); // Stores the partial message in a
										// Buffer
			} else {
				/* byte[] deliver_array = new byte[length];
				long date = buffer.getLong();
				buffer.get(deliver_array, 0, length);
				Message mess = new Message(date, deliver_array);
				((NioEngine)engine).addToQueue(mess, null);
				String mot = String.valueOf(date); */
				
				/*Version Step 2-3*/
				byte[] deliveer_array = new byte[length];
				int sender_id = buffer.getInt();
				int lamport_timestamp = buffer.getInt();
				buffer.get(deliveer_array, 0, length);
				long checksum = buffer.getLong();
				
				//Penser à enlever le message de la queue si le checksum n'est pas bon
				if(checkMessage(deliveer_array,checksum)){
					Message m = new DataMessage(lamport_timestamp, sender_id,deliveer_array);
					((NioEngine)engine).addToMap2(m);
					
					for (Channel channel : ((NioEngine) engine)
							.getChannelList()) {
						//((NioChannel)channel).sendAck(m);
					}
					
				}
				// on envoit le ack aux autres
				/*if (!nouveau_venu)
					for (Channel channel : ((NioEngine) engine)
							.getChannelList()) {
						channel.send(mot.getBytes(), 0, length);
					}*/

			}
			break;
			
		case 1:
			// Reception d'un Ack
			int id_sender = buffer.getInt();
			int lamport_timestamp = buffer.getInt();
			int message_emitter = buffer.getInt();
			int message_timestamp = buffer.getInt();
			ByteBuffer ack_payload = ByteBuffer.allocate(8);
			ack_payload.putInt(message_emitter);
			ack_payload.putInt(message_timestamp);
			ack_payload.flip();
			Message m = new AckMessage(lamport_timestamp,id_sender,ack_payload.array());
			((NioEngine)engine).addToMap2(m);
			break;
			
		case 2:
			// reception d'une demande pour rejoindre le groupe -> On broadcast
			for (Channel channel : ((NioEngine) engine).getChannelList()) {
				if (!channel.equals(this)) {
					byte[] deliver_array1 = new byte[length];
					buffer.get(deliver_array1, 0, length);
					
					channel.send(deliver_array1, 0, length);
				}
			}

			break;
		case 3:
			// reception d'un nouveau venu
			if (length > buffer.remaining()) {
				bufferstate = new BufferState(length, length
						- buffer.remaining());
				rcv_buffer.put(buffer); // Stores the partial message in a Buffer
			} else {
				byte[] deliver_array1 = new byte[length];
				buffer.get(deliver_array1, 0, length);
				String ip_port = new String(deliver_array1);
				String ip = ip_port.substring(0, ip_port.indexOf(":"));
				String port = ip_port.substring(ip_port.indexOf(":") + 1);
				System.out.println(port);
				try {
					NioChannel new_channel = new NioChannel(this.engine,
							InetAddress.getByName("localhost"),
							Integer.valueOf(port), this.local_port);
				} catch (NumberFormatException | SecurityException
						| IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			break;
		}
		
		if (buffer.hasRemaining()) {
			handleMessage(buffer);
		}


	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
	}

	@Override
	public void send(byte[] bytes, int offset, int length) {
		// System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
		/*if (send_buffer.capacity() - send_buffer.position() > length + 21) {

			try {				
				send_buffer.putInt(length+17);
				send_buffer.put((byte)0);
				send_buffer.putInt(((NioEngine)engine).getId());
				send_buffer.putInt(((NioEngine)engine).getTimestamp());
				if (bytes != null)
					send_buffer.put(bytes, offset, length);
				Checksum checksum = new CRC32();
				checksum.update(bytes, offset, length);
				long checksum_value = checksum.getValue();
				send_buffer.putLong(checksum_value);
				
			} catch (BufferOverflowException e) {
				System.out.println("Send Buffer is Full");
			}
			selectionkey.interestOps(SelectionKey.OP_READ
					| SelectionKey.OP_WRITE);

		} else {
			System.out.println("Send Buffer is Full");
		}*/

	}	
	
	public void sendmessage(byte[] bytes, int offset, int length, long date) {
		// System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
		// if(((NioEngine)engine).getChannel_list().size()%3==0)
		// send_buffer.clear();
		boolean overflow = false;

		if (send_buffer.capacity() - send_buffer.position() > length + 8) {
			boolean putin = false;
			boolean me = false;
			boolean putdate = false;
			boolean pubyte = false;

			while (!(putin && me && putdate && pubyte)) {
				try {
					if (!putin)
						send_buffer.putInt(length);
					putin = true;
					

					if (!me)
						send_buffer.putInt(0);
					me = true;
					

					if (!putdate)
						send_buffer.putLong(date);
					putdate = true;
					

					if (bytes != null)
						if (!pubyte)
							send_buffer.put(bytes, offset, length);
					pubyte = true;
					
				} catch (BufferOverflowException e) {
					System.out.println("Send Buffer is Full");
					send_buffer.compact();
				}

			}

			selectionkey.interestOps(SelectionKey.OP_READ
					| SelectionKey.OP_WRITE);
		} else {
			System.out.println("Send Buffer is Full");
		}

	}

	public void checkbuffer(ByteBuffer buffer) {
		int taille = buffer.capacity();
		int remain = buffer.position();
		if ((((taille - remain) / taille)) < 0.25)
			buffer.compact();
	}
	
	public boolean checkMessage(byte [] payload,long checksum){
		Checksum sum_control = new CRC32();
		sum_control.update(payload, 0, payload.length);
		long checksum_value = sum_control.getValue();
		return checksum_value == checksum ;
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

	public DeliverCallback getDelivercallback() {
		return delivercallback;
	}

	public void setDelivercallback(DeliverCallback delivercallback) {
		this.delivercallback = delivercallback;
	}

	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}

}
