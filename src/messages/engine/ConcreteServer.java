package messages.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class ConcreteServer extends Server {
	
	private ServerSocketChannel serverchannel ;
	private int listening_port ;
	private Engine engine ;
	
	public ServerSocketChannel getServerchannel() {
		return serverchannel;
	}

	public ConcreteServer(ServerSocketChannel serveur){
		serverchannel = serveur ;		
	}
	
	public ConcreteServer(Engine engine, int port){
		
	}

	@Override
	public int getPort() {
		try {
			return ((InetSocketAddress) serverchannel.getLocalAddress()).getPort();
		} catch (IOException e) {
			e.printStackTrace();
			return 0 ;
		}
	}

	@Override
	public void close() {
		System.out.println("Closing Server.");
		try {
			serverchannel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

}
