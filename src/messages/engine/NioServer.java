package messages.engine;

import java.io.IOException;

public class NioServer {
	
	private Engine engine ;
	private Server server ;
	private int listening_port ;
	private AcceptCallback acceptcallback ;
	
	public NioServer(Engine moteur,int port) throws IOException{
		this.engine = moteur ;
		this.listening_port = port ;
		this.acceptcallback = new NioAccept(engine,this);
		this.server = engine.listen(listening_port, acceptcallback);
		this.setPort(server.getPort());
	}
	
	public Engine getEngine() {
		return engine;
	}
	public void setEngine(Engine engine) {
		this.engine = engine;
	}
	public Server getServer() {
		return server;
	}
	public void setServer(Server server) {
		this.server = server;
	}
	public int getPort() {
		return listening_port;
	}
	public void setPort(int listening_port) {
		this.listening_port = listening_port;
	}
	public AcceptCallback getAcceptcallback() {
		return acceptcallback;
	}
	public void setAcceptcallback(AcceptCallback acceptcallback) {
		this.acceptcallback = acceptcallback;
	}
	
	

}
