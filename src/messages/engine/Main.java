package messages.engine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

public class Main {

	/*
	 * L'adresse pour tout les clients est localhost, donc il faut juste
	 * spécifier le port args[0] = ID args[1] = Port sur lequel on veut écouter
	 * les connexions entrantes args[2] = Port du premier peer auquel se
	 * connecter args[3] = Port du deuxième peer, etc...
	 */
	public static void main(String[] args) {
		try {
			Scanner sc = new Scanner(System.in);
			String name = args.length > 0 ? args[0] : "";

			final Engine engine = new NioEngine();

			NioServer serveur1;
			int port = 0;
			if (args.length > 1) {
				try {
					port = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					System.out
							.println("Warning : Args[1] must be an Integer (0-65535)");
				}
			}
			serveur1 = new NioServer(engine, port);

			if (args.length > 2) {
				for (int i = 2; i < args.length; i++) {
					try {
						int porte = Integer.parseInt(args[i]);
						NioChannel channel = new NioChannel(engine,InetAddress.getByName("localhost"), porte, port);						
					} catch (NumberFormatException e) {
						System.out
								.println("Warning : Args[i] must be an Integer (0-65535)");
						continue;
					}
				}
			}
			Thread thread_engine = new Thread(new Runnable() {
				public void run() {
					engine.mainloop();
				}
			}, name);
			thread_engine.start();

			/*
			 * Send random bytes to each peer In this demo, we wait there are 2
			 * peers before sending
			 */

			boolean continuer = true;
			
			Thread.sleep(2000);
			
			/*JoinGroup Request*/
			if(args.length > 2){
				int lamport_timestamp = ((NioEngine)engine).getTimestamp();
				int id_sender = ((NioEngine)engine).getId();
				Message m = new JoinGroupMessage(lamport_timestamp,id_sender);
				byte[] message_array = m.sendMessage() ;
				for (Channel channel : ((NioEngine) engine)
					.getChannelList()) {
					((NioChannel)channel).send(message_array, 0, message_array.length);
				}
			}

			while (continuer) {
				if (((NioEngine) engine).getChannelList().size() > 1) {
					for (int k = 0; k < 3; k++) {
						Random random = new Random(System.currentTimeMillis());
						int length = random.nextInt(Byte.MAX_VALUE);
						System.out.println("Length :" + length);
						
						byte bytes[] = new byte[length] ;
						for (int i = 0; i < length; i++) {
							bytes[i] = (byte) i;
						}
						
						 int lamport_timestamp = ((NioEngine)engine).getTimestamp();
						 int id_sender = ((NioEngine)engine).getId();
						 Message m = new DataMessage(lamport_timestamp,id_sender,bytes);
						 byte[] message_array = m.sendMessage() ;

						for (Channel channel : ((NioEngine) engine)
								.getChannelList()) {
							// A VOIR
							((NioChannel)channel).send(message_array, 0, message_array.length);
						}
					}
					continuer = false;
				}
			}

			/*
			 * for(;;){ System.out.println(name+" :"); String str =
			 * sc.nextLine(); String result = name+" : \n"+str ; byte []
			 * str_bytes = result.getBytes(); for( Channel channel :
			 * ((NioEngine)engine).getChannelList()){ channel.send(str_bytes, 0,
			 * str_bytes.length); } }
			 */

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
}
