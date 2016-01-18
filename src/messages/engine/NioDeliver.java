package messages.engine;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class NioDeliver implements DeliverCallback {

	private Engine engine;

	public NioDeliver(Engine engine) {
		this.engine = engine ;
	}

	public Engine getEngine(){
		return engine ;
	}

	@Override
	public void deliver(Channel channel, byte[] bytes) {

		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		buffer.put(bytes);
		int length = buffer.getInt();
		byte messageID = buffer.get();

		switch (messageID) {
		case 0:

			byte[] deliver_array = new byte[length - 17];
			int sender_id = buffer.getInt();
			int lamport_timestamp = buffer.getInt();
			buffer.get(deliver_array, 0, deliver_array.length);
			long checksum = buffer.getLong();
			for (int i = 0; i < deliver_array.length; i++) {
				System.out.print(deliver_array[i] + " ");
			}
			System.out.println("\n"
					+ "---------------------------------------------------");

			break;

		case 3:
			
			((NioChannel)channel).setBlocked(true);
			break;
		}

	}

}
