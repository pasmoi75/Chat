package messages.engine;

import java.nio.ByteBuffer;
import java.util.zip.*;
import messages.engine.NioChannel.BufferState;

public class MessageHandler {

	public void handleMessage(ByteBuffer buffer, NioChannel channel) {

		int length = buffer.getInt();
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
			((NioEngine) channel.getEngine()).addToMap2(m);
			break;

		case 2:
			// reception d'une demande pour rejoindre le groupe -> On broadcast
			id_sender = buffer.getInt();
			lamport_timestamp = buffer.getInt();
			m = new JoinGroupMessage(lamport_timestamp, id_sender);

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
			break;

		case 4:
			// Reception d'un Hello
			id_sender = buffer.getInt();
			lamport_timestamp = buffer.getInt();
			byte[] payload = new byte[length - 9];
			buffer.get(payload, 0, payload.length);

			// Update de sa propre liste des Peers

			m = new HelloMessage(lamport_timestamp, id_sender, payload);

			/* Building ACK */
//			ack_payload = ByteBuffer.allocate(8);
//			ack_payload.putInt(id_sender);
//			ack_payload.putInt(lamport_timestamp);
//			ack_payload.flip();

			// m2 = new
			// AckMessage(lamport_timestamp,id_sender,ack_payload.array());

			channel.setBlocked(false);

			// byte [] message_array = m2.sendMessage() ;
			// other_channel.send(message_array,0,message_array.length);

			break;

		case 5:
			// Reception d'une Liste de peers
			id_sender = buffer.getInt();
			lamport_timestamp = buffer.getInt();
			byte[] members_list = new byte[length - 9];
			buffer.get(members_list, 0, members_list.length);

			// Parsing de la members_list en InetAddress+port
			// On se connecte à tout les pairs
			// Envoi d'un HelloMessage à tout le monde
		}

	}

	public boolean checkMessage(byte[] payload, long checksum) {
		Checksum sum_control = new CRC32();
		sum_control.update(payload, 0, payload.length);
		long checksum_value = sum_control.getValue();
		return checksum_value == checksum;
	}
}
