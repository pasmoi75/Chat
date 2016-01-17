package messages.engine;

public class NioAccept implements AcceptCallback {
	
	private NioServer nioserver ;
	private Engine engine ;
	
	public NioAccept(Engine engine,NioServer serveur){
		this.engine = engine ;
		this.nioserver = serveur ;
	}
	
	public Server getServer(){
		return nioserver.getServer();
	}
	
	@Override
	public void accepted(Server server, Channel channel) {
		System.out.println("New Inconming connection from peer : "+channel.getRemoteAddress().toString());		
		((NioEngine)engine).getChannelList().add(channel);
		engine.acceptCount++;
	}

	@Override
	public void closed(Channel channel) {
		((NioEngine)engine).getChannelList().remove(channel);
	}

}
