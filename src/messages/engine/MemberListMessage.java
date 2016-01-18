package messages.engine;

/* MEMBERLIST : Length = 9+X | ID_MESS(1) | ID_SENDER(4) | LOGICAL_CLOCK(4) | LIST_MEMBERS(X) */
public class MemberListMessage extends Message {

	public MemberListMessage(int lamport_stamp, int sender_id, byte[] payload) {
		super(lamport_stamp, sender_id, payload);
		messageID = 5 ;
	}

}	
