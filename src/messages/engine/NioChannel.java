package messages.engine;

import java.io.IOException;
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
	
	private Engine engine ;
	private SelectionKey selectionkey ;
	private SocketChannel channel ;
	private ByteBuffer rcv_buffer ;
	private ByteBuffer send_buffer ;
	private DeliverCallback delivercallback ;
	private ConnectCallback connectcallback ;
	
		
	public class BufferState {
		public int messagelength ;
		public int remaining ;

		public BufferState(int length,int remain){
			messagelength = length ;
			remaining = remain ;
		}
	}
	
	private BufferState bufferstate ;
	
	/* Constructor for Incoming connections */
	public NioChannel(Engine engine,SocketChannel channel){
		this.engine = engine ;
		this.channel = channel ;
		this.delivercallback = new NioDeliver(this);
		this.connectcallback = new NioConnect(engine,this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 8);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
	}
	
	/* Constructor for Outgoing connections */
	public NioChannel(Engine engine,InetAddress hostAddress, int port) throws UnknownHostException, SecurityException, IOException{
		this.engine = engine ;
		this.delivercallback = new NioDeliver(this);
		this.connectcallback = new NioConnect(engine,this);
		this.rcv_buffer = ByteBuffer.allocate(1 << 19);
		this.send_buffer = ByteBuffer.allocate(1 << 19);
		this.engine.connect(hostAddress, port, connectcallback);
		
	}
	
	public ConnectCallback getConnectcallback() {
		return connectcallback;
	}

	public ByteBuffer getReceiveBuffer() {
		return rcv_buffer;
	}
	
	public ByteBuffer getSendBuffer(){
		return send_buffer ;
	}

	public void setChannel(SocketChannel channel){
		this.channel = channel ;
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
		this.delivercallback = callback ;
	}
	
	public DeliverCallback getDeliverCallback(){
		return delivercallback ;
	}
	
	/* Method which handles and parses received messages */
	public void handleMessage(ByteBuffer buffer){
		
		
		
		
		
		int length = buffer.getInt() ;	
		int id = buffer.getInt();
		
		switch (id) {
		case 0:
			// reception simple de message
			if(length > buffer.remaining()){
				bufferstate = new BufferState(length,length-buffer.remaining());
				rcv_buffer.put(buffer); //Stores the partial message in a Buffer
				rcv_buffer.position(rcv_buffer.position()-buffer.remaining());			
			} else {
				byte[] deliver_array = new byte[length];	
				buffer.get(deliver_array, 0, length);
				delivercallback.deliver(this,deliver_array);	
			}
			if(buffer.hasRemaining()){
				handleMessage(buffer);
			}
			
			break;
		case 1:
			// Reception d'un Ack
			Long timestamp = buffer.getLong();
			engine.addToMap(null, timestamp);			
			
			break;
		case 2:
			// reception d'une demande pour rejoindre le groupe
			break;
		case 3:
			// reception d'un nouveau venu 
			break;
		}
		
		
									
		if(length > buffer.remaining()){
			bufferstate = new BufferState(length,length-buffer.remaining());
			rcv_buffer.put(buffer); //Stores the partial message in a Buffer
			rcv_buffer.position(rcv_buffer.position()-buffer.remaining());			
		} else {
			byte[] deliver_array = new byte[length];	
			buffer.get(deliver_array, 0, length);
			delivercallback.deliver(this,deliver_array);	
		}
		if(buffer.hasRemaining()){
			handleMessage(buffer);
		}	
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
	}

	@Override
	public void send(byte[] bytes, int offset, int length) {
		//System.out.println("Buffer position : "+send_buffer.position()+" \nBuffer capacity :"+send_buffer.capacity()+" \nBuffer Limit :"+send_buffer.limit());
		
		if(send_buffer.capacity() - send_buffer.position() > length+4){
		send_buffer.putInt(length);
		send_buffer.put(bytes, offset, length);
		selectionkey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
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
	

}
