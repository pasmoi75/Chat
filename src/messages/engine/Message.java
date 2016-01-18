package messages.engine;

import java.nio.ByteBuffer;
import java.util.Date;

/* Message : Length = 9+X | ID_MESS (1) | ID_SENDER (4)| LogcialClock (4) | PAYLOAD (X) | */

public abstract class Message implements Comparable<Message> {

	public int timestamp ;
	public int id_sender ;
	public byte messageID ;
	public int nb_ack = 1 ;
	public byte[] message ;
	public long date ;
	
	/*Dans le cas d'un ACK*/
	public int message_emitter=0 ;
	public int message_timestamp=0 ;
	
	

	public Message(Long date, byte[] mess) {
		this.message=mess;
		this.date=date;
	}
	
	public Message(int lamport_stamp, int sender_id, byte [] payload){
		this.timestamp = lamport_stamp ;
		this.id_sender = sender_id ;
		this.message = payload ;
	}
	
	public void setPayload(byte[] payload){
		this.message = payload ;
	}


	@Override
	public int compareTo(Message o) {
		return (((Integer)timestamp).compareTo(o.timestamp));
	}
	
	@Override
	public boolean equals(Object o){
		if (!(o instanceof Message)){
			 return false ;
		} else {
			Message m = (Message) o ;
			return (((Integer)timestamp).equals(m.timestamp) && ((Integer)id_sender).equals(m.id_sender));
		}			
	}
	
	public byte[] sendMessage(){
		System.out.println("Sending "+this.getClass().getName() +" Message Timestamp : "+this.timestamp+" ID Sender : "+this.id_sender);
		
		int payload_length = (message == null)?0:message.length ;
		ByteBuffer buffer = ByteBuffer.allocate(9+4+payload_length);
		buffer.putInt(9+payload_length);
		buffer.put(messageID);
		buffer.putInt(this.id_sender);
		buffer.putInt(this.timestamp);
		if(message != null)
			buffer.put(message);
		
		buffer.flip();
		return buffer.array();
	}

}
