import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

public class TFTPServer 
{
	public static int TFTPPORT;
  public static String READDIR; 
	public static String WRITEDIR; 
	// OP codes


	public static void main(String[] args) throws IOException {
		if (args.length > 3 || args.length < 3) 
		{
			System.err.printf("usage: java %s: [port] [readDirectory] [writeDirectory] \n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try 
		{
      TFTPPORT = Integer.parseInt(args[0]);
      READDIR = (args[1] + "/");
      WRITEDIR = (args[2] + "/");
			TFTPServer server = new TFTPServer();
			server.start();
		}
		catch (SocketException e) 
			{e.printStackTrace();}
	}

	private void start() throws SocketException 
	{
		byte[] buf= new byte[TFTPUtils.BUFSIZE];
		
		// Create socket
		DatagramSocket socket= new DatagramSocket(null);
		
		// Create local bind point 
		SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests 
		while (true) 
		{        
			
			final InetSocketAddress clientAddress = receiveFrom(socket, buf);
			
			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null) 
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() 
			{
				public void run() 
				{
					try 
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);						
						
						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == TFTPUtils.OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), clientAddress.getAddress(), clientAddress.getPort());  
								
						// Read request
						if (reqtype == TFTPUtils.OP_RRQ) 
						{      
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), TFTPUtils.OP_RRQ);
						}
						// Write request
						else 
						{                       
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(), reqtype);  
						}
						sendSocket.close();
					} 
					catch (SocketException e) 
						{e.printStackTrace();}
				}
			}.start();
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) 
	{
    InetSocketAddress socketAddress = null;

		// Create datagram packet
		DatagramPacket p = new DatagramPacket(buf, buf.length);
		// Receive packet
    try {

      socket.receive(p);

      // Downcast the socketaddress to an InetSocketAddress
			socketAddress = (InetSocketAddress) p.getSocketAddress();
      // Retrieve the data from the DatagramPacket
			buf = p.getData();

    } catch(IOException e) {
      System.err.println(e.getMessage());
      e.printStackTrace();
      socketAddress = null; 
    }

		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) 
	{
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
		int opcode = TFTPUtils.getOpcode(buf);
   	// Iterating through the bytes in the Request, as each 'section' is divided by a 0 byte
		// Skip the first 2 bytes (opcode)
		// | 2 Bytes: OpCode | n bytes: Filename | 0 Byte |
		// When the 0 byte has been reached, the filename has been retrieved
		for (int i = 2; i < buf.length; i++) {
			if (buf[i] == 0) {
				break;
			} else {
				requestedFile.append((char) buf[i]);
			}
		}
		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) 
	{	
    boolean result = false;
    System.out.println("Recieved OPCODE: " + opcode);	
		if(opcode == TFTPUtils.OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			result = send_DATA_receive_ACK(sendSocket, requestedFile);
		}
		else if (opcode == TFTPUtils.OP_WRQ) 
		{
			result = receive_DATA_send_ACK(sendSocket, requestedFile);
		}
		else 
		{
			System.err.println("Invalid request. Sending an error packet.");
			send_ERR(sendSocket, new IllegalArgumentException("Invalid Opcode"));
			return;
		}		

    if (result) {
      System.out.println("Transmit was successful");
    } else {
      System.out.println("Transmit was not successful");
    }
	}


    /**
   * Sends a requested file in blocks to the client via the sendsocket, receives ack packages for each
   * sent file-data package.
   * 
   * @param sendSocket
   * @param requestedFile
   * @return
   */
	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requestedFile)  {
    boolean successfullTransmit = false;
    
    try (FileInputStream fInput = getFileInputStream(requestedFile)) {
      byte[] databuffer = new byte[TFTPUtils.BUFSIZE - 4];
      byte[] datablock = null;
      int blocknbr = 0;

      System.out.println("Recieved request for file: " + requestedFile + " which is: " + fInput.available() + " bytes");
      
      do {
        blocknbr = increaseBlockNumber(blocknbr);
        datablock = createDatablock(fInput, databuffer);

        DataPacket dPacket = new DataPacket(datablock, blocknbr);
        DatagramPacket sendP = dPacket.getDatagramPacket();

        sendSocket.send(sendP);
        boolean isTransmitted = checkAck(sendSocket, blocknbr);

        if(!isTransmitted) {
          reTransmit(sendSocket, sendP, blocknbr);
        }

      } while(datablock.length >= databuffer.length);

      System.out.println("Transmission for file: " + requestedFile + " was completed successfully");
      successfullTransmit = true;

    } catch (IOException | OutOfMemoryError | IllegalArgumentException  e) {
      System.err.println(e.toString() + " was thrown");
      send_ERR(sendSocket, e);
    }
    return successfullTransmit;
  }

  /**
   * Increases the current blocknumber on the TFTP server
   */
  public int increaseBlockNumber(int currentBlocknum) {
    currentBlocknum++;
    // If the blocknumber reached 65535, reset it back to 0
    if (currentBlocknum > TFTPUtils.MAXBLOCKSIZE) {
      System.out.println("Maximum blocksize was reached, resetting back to 0");
      currentBlocknum = 0;
    }

    return currentBlocknum;
  }
  	/**
   * Receives data from the client via the DatagramSocket, responds with ack packets for every
   * successful datablock received. Writes a file with the received data when the transmission has concluded.
   *  
   * @param sendSocket the DatagramSocket connection between client and server, in which data is sent/received
   * @param requestedFile the file that shall be received from the socket
   * @return true if the transmission concluded successfully, otherwise false
   */
  private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile) {
    boolean successfullTransmit = false;
    AckPacket ack = new AckPacket(0);
    DataPacket receivedData = new DataPacket();
    
    // Open a try statement with the FileOutputStream as resource this is to ensure that the FileOutputStream always closes
    // even if an exception is thrown. Therefore always releasing the requestedFile  
    try (FileOutputStream fStream = getFileOutputStream(requestedFile)) {

      System.out.println("Ready for receiving data for file: " + requestedFile + " sending ACK");
      sendSocket.send(ack.getDatagramPacket());
      System.out.println("Receiving..");
      do {
        receivedData.receiveData(sendSocket);
        writeBytes(fStream, receivedData);

        int currentblocknum =  TFTPUtils.getBlocknumInt(receivedData.getData());
        ack = new AckPacket(currentblocknum);

        sendSocket.send(ack.getDatagramPacket());

      } while(receivedData.getPacketLength() >= (TFTPUtils.BUFSIZE - 4));

      System.out.println("Transmission complete");
      successfullTransmit = true;

    } catch(IOException | OutOfMemoryError | IllegalArgumentException e) {
      System.err.println(e.toString() + " was thrown");
      send_ERR(sendSocket, e);
    }
    return successfullTransmit;
  }

  /**
   * Writes the current receieved bytes from a DatagramPacket to a FileOutputStream
   * 
   * @param fStream the FileOutputStream which the bytes shall be written to
   * @param recievedP the DatagramPacket which contains the received bytes from the DatagramSocket
   * @throws IOException if an Exception/error occured during the writing of bytes.
   */
  public void writeBytes(FileOutputStream fStream, DataPacket recievedP) throws IOException {
    try {

      ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();
      ByteArrayInputStream  bInputStream = new ByteArrayInputStream(recievedP.getData());

      if (new File(Paths.get("/").toString()).getFreeSpace() < recievedP.getDataLength()) {
        throw new OutOfMemoryError("There's not enough disk space left for the file");
      }

      // Write each of the bytes (therefore excluding opCode and blocknumber) to a bytearrayoutputstream
      for (int i = 0; i < recievedP.getDataLength(); i++) {
        int readbyte = bInputStream.read();
        if (i >= 4) {
          bOutputStream.write(readbyte);
        }
      }
      // Turn the bytes into a bytearray and write them to the FileOutputStream
      byte[] recievedBytes = bOutputStream.toByteArray();
      fStream.write(recievedBytes);

    } catch (IOException | OutOfMemoryError error) {
      // If an IOException has occured, throw it to be handled by the send Error method and close the fileStream
      fStream.close();
      throw error;
    }    
  }
  
	/**
   * Sends an Error packet through the DatagramSocket, which includes the error message of the error which was thrown
   * Is given a Throwable as is the superclass to all the exceptions and error messages in Java
   * It is then possible to retrieve what subclass the Exception/Error is
   * 
   * @param sendSocket the DatagramSocket which the error packet shall be sent through
   * @param error the error/exception which was thrown
   */
	private void send_ERR(DatagramSocket sendSocket, Throwable error) {
    TFTPUtils.ErrorState eState = TFTPUtils.ErrorState.Undefined;
    
    if (error != null) {   
      System.out.println("Sending error for: " + error.toString());
      if ((error instanceof FileNotFoundException) || (error instanceof NoSuchFileException)) {
  
        eState = TFTPUtils.ErrorState.FileNotFound;
        
      } else if (error instanceof FileAlreadyExistsException) {
  
        eState = TFTPUtils.ErrorState.FileExists;
  
      } else if (error instanceof IllegalArgumentException) {
        
        eState = TFTPUtils.ErrorState.IllegalTFTPOperation;

      } else if (error instanceof OutOfMemoryError) {

        eState = TFTPUtils.ErrorState.AllocationExceeded; 

      } else if (error instanceof AccessDeniedException) {

        eState = TFTPUtils.ErrorState.AccessViolation;
      }
    }
    // Create the errorpacket containing the error message
    ErrorPacket errorPacket = new ErrorPacket(eState, error);
    DatagramPacket errP = errorPacket.getErrorDatagramPacket();
 
    try {
      // If the error could not be sent through the socket, connection might be dead; print stacktrace
      sendSocket.send(errP);
    } catch (IOException e) {
      // As the error package could not be sent, it is most likely not possible to send another
      // therefore; print the stacktrace. 
      e.printStackTrace();
    }
  }



  /**
   * If a sent packet was not acknowledged, the server will try to retransmit said packet 
   * It will try to resend the packet for a maximum of 'allowedTimeouts', after which the connection is declared
   * as ended, as the recipient cannot receive any packets.
   * @param sendSocket the DatagramSocket which the packet will be sent through
   * @param sendP the DatagramPacket that shall be sent
   * @param blocknbr the current blocknumber
   * @throws IOException If an error occured while sending the packet
   * @throws SocketException if the Recipient did not acknowledge the sent packet during the retransmissions
   * (SocketException is caught in IOException, however; the distinction is that this SocketException is manually thrown)
   */
  public void reTransmit(DatagramSocket sendSocket, DatagramPacket sendP, int blocknbr) throws IOException {
    long deltaTime = System.currentTimeMillis();
    int timeouts = TFTPUtils.ALLOWEDTIMEOUTS;
    boolean isAck = false;
    System.out.println("Packet was not acknowledged, retransmitting packet");
    while(timeouts > 0) {   
      if (checkAck(sendSocket, blocknbr)) {
        isAck = true;
        return;
      }      
      if (System.currentTimeMillis() - deltaTime > TFTPUtils.TIMEOUTLENGTH){
        System.out.println("Timeout, resending packet: #" + blocknbr);
        timeouts--;
        deltaTime = System.currentTimeMillis();
        
        sendSocket.send(sendP);
      }
    }
    if (!isAck) {
      throw new SocketException("Client could not Acknowledge the packet after retranmissions, ending transfer");
    }   
  }

  /**
   * Is used to check if a requested file already exists on the server, or that access should be denied.
   * 
   * @param requestedFile
   * @return a FileOutputStream that is able to append bytes on the end of a file, for the requestedFile.
   * @throws IOException if an exception is thrown, such as if the requested file already exists
   */
  public FileOutputStream getFileOutputStream(String requestedFile) throws IOException, OutOfMemoryError {
    File checkFile = new File(requestedFile);

    // Check if the requestedFile contains any violations or manual error triggers (ex. trying to get a file that access is denied to)
    checkFileTFTPErrors(requestedFile);

    if (checkFile.exists()) {
      throw new FileAlreadyExistsException("File already exists");
    } 

    // If the file did not exist, then return a fileoutputstream that can append bytes to the file
    //fStream = new FileOutputStream(checkFile, true);
    return new FileOutputStream(checkFile, true);
  }
  
  /**
   * Creates a FileInputStream on the requested file, it checks if the requestedfile contained any
   * TFTP violations, such as If the file does not exist during a get-request.
   * 
   * @param requestedFile the file that shall be checked for violations and that the FileInputStream shall be used on
   * @return a FileInputStream for the requested file, if no violations/exceptions were thrown
   * @throws IOException if an IOException was thrown (that includes exceptions such as: FileDoesNotExists exception)
   * @throws OutOfMemoryError if the requested file contained the AllocationExceeded error test, it will throw OutOfMemoryError
   */
  public FileInputStream getFileInputStream(String requestedFile) throws IOException, OutOfMemoryError {
    File checkFile = new File(requestedFile);
    
    // Check if the requestedFile contains any violations or manual error triggers (ex. trying to get a file that access is denied to)
    checkFileTFTPErrors(requestedFile);

    if (!checkFile.exists()) {
      throw new FileNotFoundException("File does not exist on server");
    }

    return new FileInputStream(checkFile);
  }


  private void checkFileTFTPErrors(String requestedFile) throws IOException, OutOfMemoryError {
    System.out.println("Checking violations..");

    System.out.println(requestedFile);
    File checkFile = new File(requestedFile);
    // Hardcoded test to verify that TFTP Error Code 0 works as intented
    if (requestedFile.contains(("TFTP_UNDEFINED_ERROR_0"))) {
      System.out.println("Undefined test thrown");
      throw new IOException("Test: Undefined error was triggered");
    }
    // Hardcoded test to verify that TFTP Error Code 3 works as intented
    if (requestedFile.contains("TFTP_ALLOCATION_ERROR_3")) {
      System.out.println("Allocation test thrown");
      // Allocating an array of the maximum value of an integer, resulting in OutOfMemoryError
      byte[] largeArr = new byte[Integer.MAX_VALUE];
    }

    if (checkFile.exists()) {
      // Deny access if the file is either unable to be read by the server (or a hidden file), or it is a directory
      if (checkFile.isDirectory() || checkFile.isHidden() || !checkFile.canRead()) {
        throw new AccessDeniedException("Access was denied for requested file: " + requestedFile);
      }
    }
  } 
  /**
   * Tries to receive an ack packet from the DatagramSocket, if a packet is received
   * it will compare the blocknumber given in the received packet with the blocknumber the TFTPServer currently has
   * 
   * @param sendSocket the DatagramSocket to received data from
   * @param blocknbr the current blocknumber for the TFTPServer
   * @return true if the received blocknumber matches the TFTPServer blocknumber, otherwise false
   * @throws IOException if an exception occured while receiving a packet, it will throw IOException
   */
  private boolean checkAck(DatagramSocket sendSocket, int blocknbr) throws IOException {
		boolean acknowledged = false;
    AckPacket ack = new AckPacket();

    try {
      
      ack.receivedAck(sendSocket);

    } catch(SocketTimeoutException e) {
      return false;
    }

    int receivedBlocknbr = TFTPUtils.getBlocknumInt(ack.getAckBlocknumArray());
    if (receivedBlocknbr == blocknbr) {
			acknowledged = true;
		}

		return acknowledged;
	}

  /**
	 * Is used to extract bytes up to the length of the databuffer from the Byteinputstream
	 * The datablocks-array result can then be sent through the tftp protocol

	 * @param bInputStream the bytearrayinputstream to read bytes from
	 * @param databuffer the amount of bytes that shall be read
	 * @return  a bytearray containing bytes up to the size of the given databuffer
	 * @throws NullPointerException as the datablock is initialized as null, could throw error if not replaced
	 */
	private byte[] createDatablock(FileInputStream fileInputStream, byte[] databuffer) throws IOException, NullPointerException {
    ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();
    int readbyte = 0;
    int bytesRead = 0;
    byte[] datablock = null;

    do {

      readbyte = fileInputStream.read();

      // If the read byte is -1, we've reached the end of the bytearrayinputstream 
      if (readbyte == -1) {
        break;
      }

      // Write each read byte into a bytearrayoutputstream
      bOutputStream.write(readbyte);
      bytesRead++;

    } while(bytesRead < (databuffer.length));

    // When the bytes have been extracted, retrieve a bytearray from the bytearrayoutputstream
    datablock = bOutputStream.toByteArray();

    return datablock;
  }
}