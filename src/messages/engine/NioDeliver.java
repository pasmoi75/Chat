package messages.engine;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class NioDeliver implements DeliverCallback {

	private NioEngine engine;

	public NioDeliver(NioEngine engine) {
		this.engine = engine ;
	}

	public Engine getEngine(){
		return engine ;
	}

	@Override
	public void deliver(Channel channel, byte[] bytes) {

		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		buffer.put(bytes);
		buffer.flip();
		int length = buffer.getInt();
		byte messageID = buffer.get();

		switch (messageID) {
		case 0:

			byte[] deliver_array = new byte[length - 9];
			int sender_id = buffer.getInt();
			int lamport_timestamp = buffer.getInt();
			buffer.get(deliver_array, 0, deliver_array.length);
			for (int i = 0; i < deliver_array.length; i++) {
				System.out.print(deliver_array[i] + " ");
			}
			System.out.println("\n"
					+ "---------------------------------------------------");
			break;

		case 3:			
			sender_id = buffer.getInt();
			lamport_timestamp = buffer.getInt();
			
			/*Si on est à l'origine du BroadcastJoin, on envoie d'abord la liste des autres peers avant de nous même se bloquer*/
			if(engine.getId() == sender_id){
				engine.setTimestamp(engine.getTimestamp()+1);
				Message m = new MemberListMessage(engine.getTimestamp(), engine.getId(), engine.getPeersList());
				byte[] message_array = m.sendMessage();
				for(Channel other_channel : engine.getChannelList()){
					if(((NioChannel)other_channel).isNouveauvenu()){
						other_channel.send(message_array, 0, message_array.length);
						((NioChannel)other_channel).setNouveauvenu(false);
					}
				}
			}			
			((NioChannel)channel).setBlocked(true);
			break;
		}

	}

}
