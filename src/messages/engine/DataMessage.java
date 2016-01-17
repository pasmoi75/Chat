package messages.engine;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* Data : Length = 9+X | ID_MESS (1 byte) | ID_SENDER | LogcialClock | Payload (including 8 bytes for Checksum) | */
public class DataMessage extends Message {

	public DataMessage(int lamport_stamp, int sender_id, byte[] payload) {
		super(lamport_stamp, sender_id, payload);
		messageID = 0 ;
		
		ByteBuffer buffer = ByteBuffer.allocate(payload.length+8);
		Checksum checksum = new CRC32();
		checksum.update(payload, 0, payload.length);
		long checksum_value = checksum.getValue();
		buffer.putLong(checksum_value);
		buffer.flip();
		message = buffer.array();
	}

}
