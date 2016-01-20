package messages.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class NioChannel extends Channel {
	
	public final static int READING_LENGTH = 1 ;
	public final static int READING_MESSAGEID = 2 ;
	public final static int READING_SENDERID = 3 ;
	public final static int READING_CLOCK = 4 ;
	public final static int READING_PAYLOAD = 5 ;
	
	public Queue<byte[]> file_messages = new ArrayDeque<byte[]>();
	
	private int status ;
	private int status_remaining ;
	private boolean incomplete_message ;
	private int nextpayloadlength = -1 ;

	private Engine engine;
	private SelectionKey selectionkey;
	private SocketChannel channel;
	private ByteBuffer rcv_buffer;
	private ByteBuffer send_buffer;
	private DeliverCallback delivercallback;
	private ConnectCallback connectcallback;
	private boolean blocked;
	private boolean nouveau_venu ;
	private Integer local_port;
	public final Lock mutex ;
	
	public class PartialMessage {
		byte[] next_message_length ;
		byte next_message_ID ;
		byte[] next_message_sender_ID ;
		byte[] next_message_timestamp ;
		byte[] next_message_payload ;
		
		public PartialMessage(){
			next_message_length = new byte[4] ;
			next_message_sender_ID = new byte[4];
			next_message_timestamp = new byte[4];
		}
		
	}
	
	public PartialMessage partialmessage ;


	/* Constructor for Incoming connections */
	public NioChannel(Engine engine, SocketChannel channel) {
		this.engine = engine;
		this.channel = channel;
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 15);
		this.send_buffer = ByteBuffer.allocate(1 << 15);
		this.nouveau_venu = true ;
		mutex = new ReentrantLock(true);
	}

	/* Constructor for Outgoing connections */
	public NioChannel(Engine engine, InetAddress hostAddress, int port,
			int local_port) throws UnknownHostException, SecurityException,
			IOException {
		this.engine = engine;
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 15);
		this.send_buffer = ByteBuffer.allocate(1 << 15);
		this.engine.connect(hostAddress, port, connectcallback);
		this.local_port = local_port;
		this.nouveau_venu = false ;
		mutex = new ReentrantLock(true);

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

	public int getNextpayloadlength() {
		return nextpayloadlength;
	}

	public void setNextpayloadlength(int nextpayloadlength) {
		this.nextpayloadlength = nextpayloadlength;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
	}

	@Override
	public synchronized void send(byte[] bytes, int offset, int length) {
		mutex.lock();
		int count = 0 ;
		byte [] waiting_message = file_messages.peek();
		while(waiting_message != null && count < 30){
			System.out.println("Sending Waiting Message. Length = "+waiting_message.length);
			if (send_buffer.capacity() - send_buffer.position() > waiting_message.length) {
				send_buffer.put(waiting_message);
				selectionkey.interestOps(SelectionKey.OP_READ
						| SelectionKey.OP_WRITE);
				file_messages.poll();
				System.out.println("Retrieving Message ID = "+waiting_message[4]+" and Timestamp = "+Util.readInt32(waiting_message,9)+" from the queue");
			} else {
				System.out.println("Send waiting Buffer is Full");
				if(bytes != null)
				file_messages.add(bytes);
				mutex.unlock();
				return ;
			}
			waiting_message = file_messages.peek();
			count++ ;
		}
		
		System.out.println("Sending Message. Length = "+length);
		if(bytes != null){
		//System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
		if (send_buffer.capacity() - send_buffer.position() > length) {
			send_buffer.put(bytes);
			selectionkey.interestOps(SelectionKey.OP_READ
					| SelectionKey.OP_WRITE);
		} else {
			System.out.println("Putting into queue.");
			file_messages.add(bytes);
		}
		}
		mutex.unlock();
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

	public Integer getLocal_port() {
		return local_port;
	}

	public boolean isBlocked() {
		return blocked;
	}
	
	public void setIncompleteMessage(boolean boule){
		this.incomplete_message = boule ;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
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
	
	public boolean isNouveauvenu(){
		return nouveau_venu ;
	}
	
	public void setNouveauvenu(boolean boule){
		nouveau_venu = boule ;
	}
	
	public void setStatus(int statut,int remaining){
		this.status = statut ;
		this.status_remaining = remaining ;
		this.incomplete_message = true ;
	}
	
	public int getStatus(){
		return status ;
	}
	
	public int getStatusRemaining(){
		return status_remaining ;
	}
	
	public boolean isIncompleteMessage(){
		return incomplete_message ;
	}

}
