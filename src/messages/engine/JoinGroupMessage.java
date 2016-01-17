package messages.engine;

/* JOINGROUP : Length = 9 | ID_MESS(1) | ID_SENDER (4)| LogcialClock (4) | NO PAYLOAD */
public class JoinGroupMessage extends Message {

	public JoinGroupMessage(int lamport_stamp, int sender_id) {
		super(lamport_stamp, sender_id, null);
		messageID = 2 ;
	}

}
