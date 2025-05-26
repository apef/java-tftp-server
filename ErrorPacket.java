import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;

/**
 * Represents a TFTP protocol Error Packet
 */
public class ErrorPacket {
  private TFTPUtils.ErrorState eState;
  private Throwable error;
  private final int ERR_UNDEFINED = 0;
  private final int ERR_FILENOTFOUND = 1;
  private final int ERR_NOACCESS = 2;
  private final int ERR_ILLEGAL_TFTP_OPERATION = 4;
  private final int ERR_FILEEXISTS = 6;
  private final int ERR_AllocationExceeded = 3;

  public ErrorPacket(TFTPUtils.ErrorState eState, Throwable error) {
    this.eState = eState;
    this.error = error;
  }

  /**
   * Create a DatagramPacket of the ErrorPacket object.
   * @return a DatagramPacket
   */
  public DatagramPacket getErrorDatagramPacket() {
    byte[] errorPacketData = null;
    byte errCode = 0;

    // Add the corresponding error code according to the TFTP protocol
    switch(this.eState) {
      case Undefined: errCode = ERR_UNDEFINED; break;
      case FileNotFound: errCode = ERR_FILENOTFOUND; break;
      case AccessViolation:  errCode = ERR_NOACCESS; break;
      case FileExists:  errCode = ERR_FILEEXISTS; break;
      case IllegalTFTPOperation: errCode = ERR_ILLEGAL_TFTP_OPERATION; break;
      case AllocationExceeded: errCode = ERR_AllocationExceeded; break;
    }
    // Create a DatagramPacket with a byte array containing the bytes for the Opcode, Error value and Error message
    errorPacketData = createErrByteArr(errCode, error);
    DatagramPacket errP = new DatagramPacket(errorPacketData, errorPacketData.length);
    
    return errP;
  }

  /**
   * Writes the error opcode and error message into a ByteArrayOutputStream
   * With the intention of merging them into one consecutive byte array
   * @param errCodeValue the TFTP protocol value for the error that occured
   * @param error the thrown error/exception
   * @return a consecutive byte array in the format of: | ERR_OPCODE | ERR_VALUE | ERR_MSG | 0 |
   */
  private byte[] createErrByteArr(byte errCodeValue, Throwable error)  {
    byte[] errorMsgBytes = {0};
    byte[] errOPCodeHeader = {0, TFTPUtils.OP_ERR};
    byte[] errorCode = {0, errCodeValue};
    byte[] returnErrorArr = null;
    
    if (error != null) {
      errorMsgBytes = error.getMessage().getBytes();
    }
      
    try {
      
      ByteArrayOutputStream bOut = new ByteArrayOutputStream();
      bOut.write(errOPCodeHeader);
      bOut.write(errorCode);
      bOut.write(errorMsgBytes);
      bOut.write(0);
      returnErrorArr = bOut.toByteArray();

    } catch(IOException e) {

      // If the byteArrayoutputstream could not write the error message, send undefined error
      byte[] emptyError = {0, 5, 0 ,(byte) ERR_UNDEFINED};
      return emptyError;
    }

    return returnErrorArr;
  }
} 