package messages.engine;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioConnect implements ConnectCallback {
	
	private Engine engine ;
	private Channel channel ;
	private SelectionKey selectionkey ;
	
	public NioConnect(Engine engine,Channel channel){
		this.engine = engine ;
		this.channel = channel ;
	}

	public Engine getEngine() {
		return engine;
	}

	public void setEngine(Engine engine) {
		this.engine = engine;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}
	
	public void setSocketChannel(SocketChannel canal){
		((NioChannel)channel).setChannel(canal);
	}
	
	public void setSelectionKey(SelectionKey selection){
		selectionkey = selection ;
	}
	
	public SelectionKey getSelectionKey(){
		return selectionkey ;
	}

	@Override
	public void closed(Channel channel) {
			System.out.println("Ending connection with Remote Peer :"+channel.getRemoteAddress().getHostName());
			channel.close();
			((NioEngine)engine).getChannelList().remove(channel);
	}

	@Override
	public void connected(Channel channel) {
		System.out.println("Connection successful.");
		/*String ip = ((NioChannel)channel).getChannel().socket().getLocalAddress().toString();
		String duble = ip.substring(1) + ":" + ((NioChannel)channel).getLocal_port() ;
		System.out.println("ICI : " + duble);
		byte[] bytee = duble.getBytes();*/
		
		selectionkey.interestOps(SelectionKey.OP_READ);
		selectionkey.attach(channel);
		((NioChannel)channel).setSelectionkey(selectionkey);
		((NioEngine)engine).getChannelList().add(channel);

		engine.connectCount++;
	}

}
