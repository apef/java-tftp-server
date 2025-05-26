import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
/**
 * Represents a TFTP Protocol DatagramPacket containing Data
 */
public class DataPacket {
  private byte[] data;
  private byte[] opcode = {0,TFTPUtils.OP_DAT};
  private byte[] blocknbr;
  private int dataLength;

  public DataPacket(byte[] data, int blocknbr) {
    this.data = data;
    this.blocknbr = TFTPUtils.createBlocknbrArray(blocknbr);
  }

  public DataPacket() {}

  /**
   * Recieves a DatagramPacket containing Data from the DatagramSocket
   * @param sendSocket the socket which the data will be received from
   * @throws IOException if an exception was thrown
   */
  public void receiveData(DatagramSocket sendSocket) throws IOException { 
    byte[] buf = new byte[TFTPUtils.BUFSIZE];
    DatagramPacket receivedP = new DatagramPacket(buf, buf.length);
    
    // Receive the packet
    sendSocket.setSoTimeout(100);
    sendSocket.receive(receivedP);

    // If there is an error opcode in the received packet, throw a socketException and terminate connection
    TFTPUtils.checkIfErrorPacket(receivedP.getData());
    
    this.data = receivedP.getData();
    this.dataLength = receivedP.getLength();
    int recievedBlocknbr = TFTPUtils.getBlocknumInt(receivedP.getData());
    this.blocknbr = TFTPUtils.createBlocknbrArray(recievedBlocknbr);
    this.dataLength = receivedP.getLength();
  }

  /**
   * Is used to create a Datagrampacket of the DataPacket object
   */
  public DatagramPacket getDatagramPacket() throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    
    // Write both the arrays to a byteArrayOutputStream and get a new merged byte array of them
    bOut.write(opcode);
    bOut.write(blocknbr);
    bOut.write(data);

    byte[] packetBytes = bOut.toByteArray();

    // Create a DatagramPacket of the new byte array
    DatagramPacket p = new DatagramPacket(packetBytes, packetBytes.length);
    return p;
  }
  // Returns the current length of the packet 
  // (the actual bytes within the data, excluding the appended 0-bytes when reading past end of file due to the databuffer being ex: 516)
  public int getPacketLength() {
    return this.dataLength;
  }

  public int getDataLength() {
    return this.dataLength;
  }

  public byte[] getData() {
    return this.data;
  }
}
