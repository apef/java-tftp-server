import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TFTPUtils {
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;
  public static final int BUFSIZE = 516;
  public static final int MAXBLOCKSIZE = 65535;
  public static final int TIMEOUTLENGTH = 3000;
  public static final int ALLOWEDTIMEOUTS = 5;

  /**
   * The Errors which is supported by the TFTP protocol
   */
  public enum ErrorState {
    Undefined, FileNotFound, AccessViolation, FileExists, IllegalTFTPOperation, AllocationExceeded;
  }


  /**
   * Is used to fit an integer value as a short value within a byte array
   * with the purpose of sending it as a blocknumber within a data/ack packet.
   *  
   * @param blocknbr the integer value which shall be converted into a short
   * @return a bytearray containing the short value of the provided integer
   */
  public static byte[] createBlocknbrArray(int blocknbr) {
    byte[] blocknbrs = new byte[2];
    ByteBuffer wrap = ByteBuffer.wrap(blocknbrs);
    wrap.putShort((short)blocknbr);

    return blocknbrs;
  }

   /**
   * Checks the opcode of a the data within a received packet
   * If the packet contains the Error opcode, it throws a SocketException as the Recipient is 
   * unable to receive any transmissions.
   */
  public static void checkIfErrorPacket(byte[] data) throws SocketException {
    int opCode = getOpcode(data);

    if (opCode == TFTPUtils.OP_ERR) {
      throw new SocketException("Client is dead, exiting connection");
    }
  }

    /**
   * Retrieves the OPcode for a packet.
   * 
   * @param packet a byte array in which the Opcode shall be retrieved from
   * @return the opcode as an integer
   */
	public static int getOpcode(byte[] packet) {
		byte[] blocknbrs = new byte[2];
		blocknbrs[0] = packet[0];
		blocknbrs[1] = packet[1];
		
    // Use the ByteBuffer to get the short value of the two Opcode bytes in the packet
		ByteBuffer wrap = ByteBuffer.wrap(blocknbrs);
		short sblocknbr = wrap.getShort();

    // Cast the short value into an Integer and return
		return (int) sblocknbr;
	}

   /**
   * Retrieves an integer value of the current blocknumber within a received Datagram packet's byte array
   * If the byte array's size is less than 4 (meaning that the actual positions of the blocknumber bytes are not 2 and 3)
   * Then it will use the two last positions in the recieved byte array as blocknumber.
   * @param packet
   * @return
   */
  public static int getBlocknumInt(byte[] packet) {
    // If the received packet is less than 2 bytes, something went wrong during receiving
    if (packet.length < 2) {
      throw new IllegalArgumentException(" Possible Malformed packet. Given packet data has to be more than 1 in size");
    }

    byte[] blocknbrs;
    // Create a new array that only contains the blocknumber bytes from the received byte array
    if (packet.length <= 4) {
      // As the creation of byte arrays for the DataPacket and AckPacket objects involve byte arrays of length 2
      // This method should still be able to retrieve an integer value from bytes arrays, as long as they are equal to or more than 2 bytes in size 
      blocknbrs = Arrays.copyOfRange(packet, packet.length - 2, packet.length);
    } else {
      blocknbrs = Arrays.copyOfRange(packet, 2, 4);
    }
    ByteBuffer wrap = ByteBuffer.wrap(blocknbrs);

    // Get the short value stored on the two byte positions
    short shortValue =  wrap.getShort();
    // To turn the value of the short into a value on 16-bits (not signed short)
    // Set the value in an UnsignedInt, which retrieves an int value with the high values as 0
    // and the 16-bits of the short as untouched. -> Leading to possible blocknum values from 0 -> 65535
    return Short.toUnsignedInt(shortValue);
  } 
}
