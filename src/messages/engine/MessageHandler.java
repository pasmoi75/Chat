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
		
		int length = read4bytes(buffer, channel, NioChannel.READING_LENGTH) ;
		byte messageID = readMessageID(buffer, channel) ;
		System.out.println("Message Length : "+length+" Message ID : "+messageID);

		switch (messageID) {
		case 0: // Reception simple d'un message
			if (length > buffer.remaining()+1) {
				System.out.println("Partial Message");
				NioChannel.BufferState state = channel.new BufferState(length,
						length - buffer.remaining());
				channel.getReceiveBuffer().put(buffer); // Stores the partial
														// message in a Buffer
			} else {
				System.out.println(length);
				byte[] deliver_array = new byte[length - 9];
				int sender_id = read4bytes(buffer, channel, NioChannel.READING_SENDERID) ;
				int lamport_timestamp = read4bytes(buffer,channel,NioChannel.READING_CLOCK);
				deliver_array = readPayload(buffer, channel, deliver_array.length);

				// Penser à enlever le message de la queue si le checksum n'est
				// pas bon
				Message m = new DataMessage(lamport_timestamp, sender_id,deliver_array);
				System.out.println("Receiving "+m.getClass().getName()+" from "+m.id_sender);
				
				/*LamportClockUpdate*/
				engine.setTimestamp(Math.max(m.timestamp,engine.getTimestamp()));
				
				((NioEngine) channel.getEngine()).addToMap2(m);

				/* Building ACK */
				ByteBuffer ack_payload = ByteBuffer.allocate(8);
				ack_payload.putInt(sender_id);
				ack_payload.putInt(lamport_timestamp);
				ack_payload.flip();
				
				engine.setTimestamp(engine.getTimestamp()+1);
				Message m2 = new AckMessage(engine.getTimestamp(), engine.getId(),
							ack_payload.array());

				engine.addToMap2(m2);
				for (Channel other_channel : ((NioEngine) channel
							.getEngine()).getChannelList()) {
						byte[] message_array = m2.sendMessage();
						other_channel.send(message_array, 0,
								message_array.length);
					}			
			}
			break;

		case 1: // Réception Ack
			int id_sender = read4bytes(buffer, channel, NioChannel.READING_SENDERID) ;
			int lamport_timestamp = read4bytes(buffer,channel,NioChannel.READING_CLOCK);
			byte[] ack_payload_array = new byte[8];
			ack_payload_array = readPayload(buffer, channel, ack_payload_array.length);
			
			Message m = new AckMessage(lamport_timestamp, id_sender,
					ack_payload_array);
			
			/*LamportClockUpdate*/
			engine.setTimestamp(Math.max(m.timestamp,engine.getTimestamp()));
			
			System.out.println("Receiving "+m.getClass().getName()+" for Message : "+m.message_timestamp+" from "+m.id_sender+". Emitted by : "+m.message_emitter);
			((NioEngine) channel.getEngine()).addToMap2(m);
			break;

		case 2:
			// reception d'une demande pour rejoindre le groupe -> On broadcast
			id_sender = read4bytes(buffer, channel, NioChannel.READING_SENDERID) ;
			lamport_timestamp = read4bytes(buffer,channel,NioChannel.READING_CLOCK);
			m = new JoinGroupMessage(lamport_timestamp, id_sender);
			System.out.println("Receiving "+m.getClass().getName());
			
			/*LamportClockUpdate*/
			engine.setTimestamp(Math.max(m.timestamp,engine.getTimestamp()));
			engine.setTimestamp(engine.getTimestamp()+1);

			/* Broadcast à tout les autres Peers */
			if(engine.getChannelList().size() > 1){
			Message m2 = new BroadcastJoinMessage(engine.getTimestamp(), engine.getId());
			engine.addToMap2(m2);
			for (Channel other_channel : ((NioEngine) channel.getEngine())
					.getChannelList()) {
				if (!other_channel.equals(channel)) {
					byte[] message_array = m2.sendMessage();
					other_channel.send(message_array, 0, message_array.length);
				}
			}
			
			
			/*On ACK le message soi même, étant donné que pour un BroadcastJoin seul n-1 personnes vont recevoir le broadcast */
			/* Building ACK */
			ByteBuffer ack_payload = ByteBuffer.allocate(8);
			ack_payload.putInt(engine.getId());
			ack_payload.putInt(engine.getTimestamp());
			ack_payload.flip();
			
			engine.setTimestamp(engine.getTimestamp()+1);
			m2 = new AckMessage(engine.getTimestamp(), engine.getId(),
						ack_payload.array());
			engine.addToMap2(m2);
			} else if (engine.getChannelList().size() == 1){ //Cas particulier : Pour donner un ID au peer. Voir si meilleure solution existe
				Message m2 = new BroadcastJoinMessage(engine.getTimestamp(), engine.getId());
				engine.addToMap2(m2);
				for (Channel other_channel : ((NioEngine) channel.getEngine())
						.getChannelList()) {
						byte[] message_array = m2.sendMessage();
						other_channel.send(message_array, 0, message_array.length);
					
				}
			}
			
			break;

		case 3:
			// Réception d'un BrodcastJoin : Au moment du deliver on bloque le
			// Peer.
			id_sender = read4bytes(buffer, channel, NioChannel.READING_SENDERID) ;
			lamport_timestamp = read4bytes(buffer,channel,NioChannel.READING_CLOCK);
			m = new BroadcastJoinMessage(lamport_timestamp, id_sender);
			System.out.println("Receiving "+m.getClass().getName());
			engine.addToMap2(m);
			
			/*LamportClockUpdate*/
			engine.setTimestamp(Math.max(m.timestamp,engine.getTimestamp()));
			engine.setTimestamp(engine.getTimestamp()+1);

			/* Building ACK */
			ByteBuffer ack_payload = ByteBuffer.allocate(8);
			ack_payload.putInt(id_sender);
			ack_payload.putInt(lamport_timestamp);
			ack_payload.flip();

			Message m2 = new AckMessage(engine.getTimestamp(), engine.getId(),
					ack_payload.array());
			
			engine.addToMap2(m2);
			
			
			for (Channel other_channel : ((NioEngine) channel.getEngine())
					.getChannelList()) {
				byte[] message_array = m2.sendMessage();
				other_channel.send(message_array, 0, message_array.length);
			}
			

			break ;
					
		case 4 :
				 //Reception d'un Hello	
				 id_sender = read4bytes(buffer, channel, NioChannel.READING_SENDERID) ;
				 lamport_timestamp = read4bytes(buffer,channel,NioChannel.READING_CLOCK);
			 	 byte[] payload = new byte[length-9];
			 	 payload = readPayload(buffer,channel,payload.length);
			 	 			 	
			 	 m = new HelloMessage(lamport_timestamp,id_sender,payload);
			 	 if(payload.length == 6){
			 		 engine.getPeersMap().put(id_sender, payload);
			 		 System.out.println("Updating Peers Map. New Size : "+engine.getPeersMap().size());
			 	 }
			 	 
			 	/*LamportClockUpdate*/
				engine.setTimestamp(Math.max(m.timestamp,engine.getTimestamp()));
			 	 
			 	 System.out.println("Receiving "+m.getClass().getName());
			 	 ((NioChannel)channel).setBlocked(false);
				 break ;
			  
		 case 5 :
				 //Reception d'une Liste de peers
			 	 id_sender = read4bytes(buffer, channel, NioChannel.READING_SENDERID) ;
			 	 lamport_timestamp = read4bytes(buffer,channel,NioChannel.READING_CLOCK);
			 	 byte[] members_list = new byte[length-9];
			 	 members_list = readPayload(buffer,channel,members_list.length);
			 	 
			 	 m = new MemberListMessage(lamport_timestamp,id_sender,members_list);
			 	/*LamportClockUpdate*/
					engine.setTimestamp(Math.max(m.timestamp,engine.getTimestamp()));
			 	 
			 	 System.out.println("Receiving "+m.getClass().getName());
			 	 int max_id = 1 ;
			 	 
			 	 for(int i = 0 ; i<= members_list.length - 10 ; i=i+10){
			 		 /*Reading Peer Id*/
			 		 byte[] peer_id_array = new byte[4] ;
			 		 System.arraycopy(members_list, i, peer_id_array, 0, 4);
			 		 int peer_id = Util.readInt32(peer_id_array, 0);
			 		 
			 		 max_id = Math.max(max_id, peer_id);
			 		 
			 		 /*Reading IP & Port Address*/
			 		 byte[] peer_address = new byte[6] ;
			 		 System.arraycopy(members_list, i+4, peer_address, 0, 6);
			 		 
			 		 
			 		 engine.getPeersMap().put(peer_id, peer_address);
			 		 byte[] ip_address = new byte[4];
			 		 byte[] port_array = new byte[4];
			 		 
			 		 System.arraycopy(peer_address,0 , ip_address, 0, 4);
			 		 System.arraycopy(peer_address,4,port_array,2,2);
			 		 int port = Util.readInt32(port_array, 0);
			 		 
			 		 if(peer_id != id_sender){
			 		 try {
						NioChannel new_channel = new NioChannel(engine,InetAddress.getByAddress(ip_address),port,0);
						new_channel.setNouveauvenu(false);
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
			 	 }
			 	 
			 	 engine.setId(max_id+1);
			 	 
			 	 
			 	 /*Envoi d'un message Hello à tout le monde*/
			 	 InetAddress localaddress = InetAddress.getByName("localhost");
			 	 int listening_port = engine.getListeningPort();
			 	 
			 	 byte[] hello_payload = new byte[6] ;
			 	 System.arraycopy(localaddress.getAddress(),0,hello_payload,0, 4);
			 	 			 	 
			 	 byte[] port_byte4 = new byte[4];
			 	 Util.writeInt32(port_byte4, 0, listening_port);
			 	 System.arraycopy(port_byte4, 2, hello_payload, 4, 2);
			 	 
			 	 engine.getPeersMap().put(engine.getId(), hello_payload);
			 	 
			 	 engine.setTimestamp(engine.getTimestamp()+1);
			     m = new HelloMessage(engine.getTimestamp(),engine.getId(),hello_payload);
			 	 
			 	 for(Channel other_channel : engine.getChannelList()){
			 		 byte[] message_array = m.sendMessage();
			 		 other_channel.send(message_array, 0, message_array.length);			 		 
			 	 }
			 	 break ;
			 	 	
		}
		
		if(buffer.hasRemaining()){
			System.out.println("Remaining in the buffer : "+buffer.remaining());
			handleMessage(buffer,channel);
		}

	}
	
	/*Pour les méthodes ReadLength, ReadSender et ReadClock*/
	public int read4bytes(ByteBuffer buffer,NioChannel channel,int status) throws IncompleteMessageException{
		if(buffer.remaining() > 3){
			return buffer.getInt();
		}
		channel.setStatus(status, 4-buffer.remaining());
		for(int i = 0 ; i < buffer.remaining() ; i++){
			channel.getReceiveBuffer().put(buffer.get());
		}
		throw new IncompleteMessageException();
	}
	
	public byte readMessageID(ByteBuffer buffer,NioChannel channel) throws IncompleteMessageException{
		if(buffer.remaining()>0){
			return buffer.get();
		}
		channel.setStatus(NioChannel.READING_MESSAGEID,1);
		throw new IncompleteMessageException();
	}
	
	public byte[] readPayload(ByteBuffer buffer,NioChannel channel,int payload_length) throws IncompleteMessageException{
		if(buffer.remaining()>=payload_length){
			byte[] payload = new byte[payload_length];
			buffer.get(payload, 0, payload_length);
			return payload ;
		}
		channel.setStatus(NioChannel.READING_PAYLOAD, payload_length-buffer.remaining());
		for(int i = 0 ; i<buffer.remaining() ; i++){
			channel.getReceiveBuffer().put(buffer.get());
		}
		throw new IncompleteMessageException();
	}
	
	public static boolean checkMessage(byte [] payload,long checksum){
		System.out.println("Checking checksum");
		Checksum sum_control = new CRC32();
		sum_control.update(payload, 0, payload.length);
		long checksum_value = sum_control.getValue();
		System.out.println("payload sum : "+checksum_value+" Checksum : "+checksum);
		
		return true ;
	}
	
	
	
	
	
}
