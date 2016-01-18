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
	private boolean blocked;
	private int unreadposition;
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
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 8);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
	}

	/* Constructor for Outgoing connections */
	public NioChannel(Engine engine, InetAddress hostAddress, int port,
			int local_port) throws UnknownHostException, SecurityException,
			IOException {
		this.engine = engine;
		this.connectcallback = new NioConnect(engine, this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 19);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
		this.engine.connect(hostAddress, port, connectcallback);
		this.local_port = local_port;

	}

	public Engine getEngine() {
		return engine;
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

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
	}

	@Override
	// <<<<<<< HEAD
	// public void send(byte[] bytes, int offset, int length) {
	// //
	// System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
	// if (send_buffer.capacity() - send_buffer.position() > length + 21) {
	//
	// try {
	// send_buffer.putInt(length+14);
	// // send_buffer.put((byte)0);
	// send_buffer.putInt(((NioEngine)engine).getId());
	// send_buffer.putInt(((NioEngine)engine).getTimestamp());
	// if (bytes != null)
	// send_buffer.put(bytes, offset, length);
	// Checksum checksum = new CRC32();
	// checksum.update(bytes, offset, length);
	// long checksum_value = checksum.getValue();
	// send_buffer.putLong(checksum_value);
	//
	// } catch (BufferOverflowException e) {
	// System.out.println("Send Buffer is Full");
	// }
	// =======
	public synchronized void send(byte[] bytes, int offset, int length) {

//		boolean ok = false;
//
//		while(!ok) {
		System.out.println("Sending Message. Length = " + length);
		System.out.println("Buffer position : " + send_buffer.position()
				+ " \nBuffer capacity :" + send_buffer.capacity()
				+ " \nBuffer Limit :" + send_buffer.limit());

		if (send_buffer.limit() - send_buffer.position() > length) {
			send_buffer.put(bytes, offset, length);

			selectionkey.interestOps(SelectionKey.OP_READ
					| SelectionKey.OP_WRITE);
//			ok=true; 	

		} else {
//			send_buffer.compact();
			//System.out.println("Send Buffer is Full");
		}
//		}
	}

	public void checkbuffer(ByteBuffer buffer) {
		int taille = buffer.capacity();
		int remain = buffer.position();
		if ((((taille - remain) / taille)) < 0.25)
			buffer.compact();
	}

	public boolean checkMessage(byte[] payload, long checksum) {
		Checksum sum_control = new CRC32();
		sum_control.update(payload, 0, payload.length);
		long checksum_value = sum_control.getValue();
		return checksum_value == checksum;
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

}
