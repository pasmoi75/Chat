package messages.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.zip.*;
import messages.engine.NioChannel.BufferState;

public class MessageHandler {

	
	private NioEngine engine ;
	
	public MessageHandler(NioEngine engine){
		this.engine = engine ;
	}
	
	public void handleMessage(ByteBuffer buffer,NioChannel channel) throws Exception{
		
		int length = buffer.getInt() ;
		byte messageID = buffer.get();

		switch (messageID) {
		case 0: // Reception simple d'un message
			if (length > buffer.remaining()) {
				NioChannel.BufferState state = channel.new BufferState(length,
						length - buffer.remaining());
				channel.getReceiveBuffer().put(buffer); // Stores the partial
														// message in a Buffer
			} else {
				System.out.println(length);
				byte[] deliver_array = new byte[length - 17];
				int sender_id = buffer.getInt();
				int lamport_timestamp = buffer.getInt();
				buffer.get(deliver_array, 0, deliver_array.length);
				long checksum = buffer.getLong();

				// Penser à enlever le message de la queue si le checksum n'est
				// pas bon
				if (checkMessage(deliver_array, checksum)) {
					Message m = new DataMessage(lamport_timestamp, sender_id,
							deliver_array);
					System.out.println("Receiving "+m.getClass().getName());
					
					((NioEngine) channel.getEngine()).addToMap2(m);

					/* Building ACK */
					ByteBuffer ack_payload = ByteBuffer.allocate(8);
					ack_payload.putInt(sender_id);
					ack_payload.putInt(lamport_timestamp);
					ack_payload.flip();

					Message m2 = new AckMessage(lamport_timestamp, sender_id,
							ack_payload.array());
					for (Channel other_channel : ((NioEngine) channel
							.getEngine()).getChannelList()) {
						byte[] message_array = m2.sendMessage();
						other_channel.send(message_array, 0,
								message_array.length);
					}


				}
			}
			break;

		case 1: // Réception Ack
			int id_sender = buffer.getInt();
			int lamport_timestamp = buffer.getInt();
			byte[] ack_payload_array = new byte[8];
			buffer.get(ack_payload_array, 0, ack_payload_array.length);
			Message m = new AckMessage(lamport_timestamp, id_sender,
					ack_payload_array);
			System.out.println("Receiving "+m.getClass().getName());
			((NioEngine) channel.getEngine()).addToMap2(m);
			break;

		case 2:
			// reception d'une demande pour rejoindre le groupe -> On broadcast
			id_sender = buffer.getInt();
			lamport_timestamp = buffer.getInt();
			m = new JoinGroupMessage(lamport_timestamp, id_sender);
			System.out.println("Receiving "+m.getClass().getName());

			/* Broadcast à tout les autres Peers */
			Message m2 = new BroadcastJoinMessage(lamport_timestamp, id_sender);
			for (Channel other_channel : ((NioEngine) channel.getEngine())
					.getChannelList()) {
				if (!other_channel.equals(channel)) {
					byte[] message_array = m2.sendMessage();
					other_channel.send(message_array, 0, message_array.length);
				}
			}
			break;

		case 3:
			// Réception d'un BrodcastJoin : Au moment du deliver on bloque le
			// Peer.
			id_sender = buffer.getInt();
			lamport_timestamp = buffer.getInt();
			m = new BroadcastJoinMessage(lamport_timestamp, id_sender);
			System.out.println("Receiving "+m.getClass().getName());

			/* Building ACK */
			ByteBuffer ack_payload = ByteBuffer.allocate(8);
			ack_payload.putInt(id_sender);
			ack_payload.putInt(lamport_timestamp);
			ack_payload.flip();

			m2 = new AckMessage(lamport_timestamp, id_sender,
					ack_payload.array());
			for (Channel other_channel : ((NioEngine) channel.getEngine())
					.getChannelList()) {
				byte[] message_array = m2.sendMessage();
				other_channel.send(message_array, 0, message_array.length);
			}
			

					break ;
					
			 case 4 :
				 //Reception d'un Hello	
				 id_sender = buffer.getInt();
			 	 lamport_timestamp = buffer.getInt();
			 	 byte[] payload = new byte[length-9];
			 	 buffer.get(payload, 0, payload.length);
			 	 			 	
			 	 m = new HelloMessage(lamport_timestamp,id_sender,payload);
			 	 System.out.println("Receiving "+m.getClass().getName());
			 	 ((NioChannel)channel).setBlocked(false);
				 break ;
			  
			 case 5 :
				 //Reception d'une Liste de peers
				 id_sender = buffer.getInt();
			 	 lamport_timestamp = buffer.getInt();
			 	 byte[] members_list = new byte[length-9];
			 	 buffer.get(members_list, 0, members_list.length);
			 	 
			 	 m = new MemberListMessage(lamport_timestamp,id_sender,members_list);
			 	 System.out.println("Receiving "+m.getClass().getName());
			 	 
			 	 for(int i = 0 ; i<= members_list.length - 10 ; i=i+10){
			 		 /*Reading Peer Id*/
			 		 byte[] peer_id_array = new byte[4] ;
			 		 System.arraycopy(members_list, i, peer_id_array, 0, 4);
			 		 int peer_id = Util.readInt32(peer_id_array, 0);
			 		 
			 		 /*Reading IP & Port Address*/
			 		 byte[] peer_address = new byte[6] ;
			 		 System.arraycopy(members_list, i+4, peer_address, 0, 6);
			 		 
			 		 
			 		 engine.getPeersMap().put(peer_id, peer_address);
			 		 byte[] ip_address = new byte[4];
			 		 byte[] port_array = new byte[4];
			 		 
			 		 System.arraycopy(peer_address,0 , ip_address, 0, 4);
			 		 System.arraycopy(peer_address,4,port_array,2,2);
			 		 int port = Util.readInt32(port_array, 0);
			 		 
			 		 try {
						NioChannel new_channel = new NioChannel(engine,InetAddress.getByAddress(peer_address),port,0);
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SecurityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}			 		 
			 	 }
			 	 
			 	 /*Envoi d'un message Hello à tout le monde*/
			 	 InetAddress localaddress = InetAddress.getByName("localhost");
			 	 int listening_port = engine.getListeningPort();
			 	 
			 	 byte[] hello_payload = new byte[6] ;
			 	 System.arraycopy(localaddress.getAddress(),0,hello_payload,0, 4);
			 	 			 	 
			 	 byte[] port_byte4 = new byte[4];
			 	 Util.writeInt32(port_byte4, 0, listening_port);
			 	 System.arraycopy(port_byte4, 2, hello_payload, 4, 2);
			 	 
			     m = new HelloMessage(engine.getTimestamp(),engine.getId(),hello_payload);
			 	 
			 	 for(Channel other_channel : engine.getChannelList()){
			 		 byte[] message_array = m.sendMessage();
			 		 other_channel.send(message_array, 0, message_array.length);			 		 
			 	 }
			 	 	
		}

	}
	
	
	public static boolean checkMessage(byte [] payload,long checksum){
		Checksum sum_control = new CRC32();
		sum_control.update(payload, 0, payload.length);
		long checksum_value = sum_control.getValue();
		return checksum_value == checksum;
	}
	
	
	
	
	
}
