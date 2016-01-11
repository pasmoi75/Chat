package messages.engine;

import java.util.Date;

public class Message {

	byte[] message ;
	int nb_ack = 1 ;
	long date;
	

	public Message(Long date, byte[] mess) {
		this.message=mess;
		this.date=date;
		

	}

}
