package messages.engine;

import java.util.Date;

public class Message {

	int nb_ack = 0 ;
	long date;
	

	public Message() {

		Date date = new Date();
		this.date = date.getTime();
		

	}

}
