package messages.engine;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* Data : Length = 9+X | ID_MESS (1 byte) | ID_SENDER | LogcialClock | Payload (including 8 bytes for Checksum) | */
public class DataMessage extends Message {

	public DataMessage(int lamport_stamp, int sender_id, byte[] payload) {
		super(lamport_stamp, sender_id, payload);
		messageID = 0 ;

	}

}
