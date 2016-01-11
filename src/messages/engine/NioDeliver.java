package messages.engine;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class NioDeliver implements DeliverCallback {
	
	private Channel channel ;
	
	public NioDeliver(Channel channel){
		this.channel = channel ;
	}
	
	public Channel getChannel(){
		return channel ;
	}

	@Override
	public void deliver(Channel channel, byte[] bytes) {
	
			System.out.println("Received from: " + ((NioChannel)channel).getChannel().socket().getRemoteSocketAddress());
			for(int i = 0 ; i < bytes.length; i++){
				System.out.print(bytes[i]+" ");				
			}
			System.out.println("\n"+"---------------------------------------------------");
	}

}
