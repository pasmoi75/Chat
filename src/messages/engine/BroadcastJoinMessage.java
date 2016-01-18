package messages.engine;

/* BroadcastJoin : Length = 9 | ID_MESS(1) | ID_SENDER(4) | LOGICAL_CLOCK(4) | NO PAYLOAD */
public class BroadcastJoinMessage extends Message {

	public BroadcastJoinMessage(int lamport_stamp, int sender_id) {
		super(lamport_stamp, sender_id, null);
		messageID = 3 ;
	}

	
}
