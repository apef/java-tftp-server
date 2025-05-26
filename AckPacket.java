import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
/**
 * Represents an Ack packet for the TFTP protocol 
 */
public class AckPacket {
  private byte[] opcode = {0, TFTPUtils.OP_ACK};
  private byte[] blocknbr;

  // Constructor for creating an ackpacket with a blocknumber (such as when responding to client with ack)
  public AckPacket(int blocknbr) {
    this.blocknbr = TFTPUtils.createBlocknbrArray(blocknbr);
  }
  
  public AckPacket() {}
  
  /**
   * Tries to receive a DatagramPacket from the socket.
   * @param sendSocket the socket which the DatagramPacket shall be received from
   * @throws IOException if an error occured during the receiving of the packet
   */
  public void receivedAck(DatagramSocket sendSocket) throws IOException {
    byte[] ackarr = new byte[4];
    DatagramPacket ack = new DatagramPacket(ackarr, ackarr.length);
  
    try {
      sendSocket.setSoTimeout(100);
      sendSocket.receive(ack);
    } catch(SocketTimeoutException timeout) {
      throw new SocketTimeoutException("timeout");
    }
    // Retreive the data from the DatagramPacket 
    ackarr = ack.getData();

    // Check if the data contains an Error opcode
    TFTPUtils.checkIfErrorPacket(ackarr);

    // Get the new blocknumber from the received ackpacket
    int recievedBlocknum = TFTPUtils.getBlocknumInt(ackarr);
    this.blocknbr = TFTPUtils.createBlocknbrArray(recievedBlocknum);
  }

  /**
   * Is used to create a Datagrampacket of the AckPacket object
   */
  public DatagramPacket getDatagramPacket() throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    
    // Write both the byte arrays to a byteArrayOutputStream
    bOut.write(opcode);
    bOut.write(blocknbr);

    // Get the newly merged bytearray from the byteArrayOutputStream and create a DatagramPacket with it
    byte[] packetBytes = bOut.toByteArray();
    DatagramPacket p = new DatagramPacket(packetBytes, packetBytes.length);
    return p;
  }
  
  public byte[] getAckBlocknumArray() {
    return blocknbr;
  }
}
