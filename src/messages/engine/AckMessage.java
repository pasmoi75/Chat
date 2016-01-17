package messages.engine;

/* ACK : Length = 17 | ID_MESS(1) | ID_SENDER(4) | LOGICAL_CLOCK(4) | MESSAGE_EMITTER(4) | MESSAGE_TIMESTAMP(4) */
public class AckMessage extends Message{
	
	public AckMessage(int lamport_stamp, int sender_id, byte[] payload) {
		super(lamport_stamp, sender_id, payload);
		message_emitter = Util.readInt32(payload, 0);
		message_timestamp = Util.readInt32(payload, 4);
		messageID = 1 ;
	}
	
	public AckMessage(int emitter, int timestampemitter){
		super(0, 0, null);
		message_emitter=emitter;
		message_timestamp = timestampemitter;
	}
	
	
	
	/* Pour des ACK on compare les champs MESSAGE_EMITTER & MESSAGE_TIMESTAMP */	
	@Override
	public boolean equals(Object o){
		if(!(o instanceof AckMessage))
			return false ;
		else{
			AckMessage a = (AckMessage) o ;
			return a.message_emitter == message_emitter && a.message_timestamp == message_timestamp ;
		}		
	}

}
