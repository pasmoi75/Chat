package messages.engine;

/* HELLO : Length = 15 | ID_MESS(1) | ID_SENDER (4)| LogcialClock (4) | IPV4+PORT (6) */
public class HelloMessage extends Message {
	
	public HelloMessage(int lamport_stamp, int sender_id,byte[] payload) {
		super(lamport_stamp, sender_id, payload);
		messageID = 4 ;
	}

}
