# Java TFTP Server

A TFTP (Trivial File Transfer Protocol) server implemented in Java. Supports standard opcodes, read/write requests, error handling, and is compatible with standard TFTP clients. It has been tested and verified with TFTP64, and even sending to and from itself.

## Features

- Supports GET (Read) and PUT (Write) requests.
- Supports changing the Port it listens for requests on.
- Supports changing the Read and Write directory, they can be different or point to the same directory.
- Does not use any external libraries.

## Requirements
- Java JDK 8 or Later
- Terminal or command prompt access (ex: CMD, bash)

## How to compile and run the TFTP server
Open a terminal in the project folder and compile the files using the following command.
```
javac *.java
```

The Server can then be run with this command.
```
java TFTPServer [port] [readDirectory] [writeDirectory]
```

#### To manually test the error codes, if that is of interest.
  - Undefined error (0): Manually throw exception with a filename containing: "TFTP_UNDEFINED_ERROR_0".
  - File Not Found (1): Request a file that does not exist.
  - Access violation (2): Request a directory (for example, create a new directory within ReadDir and send a get request for it).
  - Disk full or allocation exceeded (3): Manually throw exception with a filename containing: "TFTP_ALLOCATION_ERROR_3".
  - Illegal TFTP operation (4): Send an invalid opcode to the server.
  - File already exists (6): Send a put request for a file that already exists on the server.
