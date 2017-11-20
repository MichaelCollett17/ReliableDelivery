import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.*;
import java.util.Timer;

import edu.utulsa.unet.RSendUDPI;
import edu.utulsa.unet.UDPSocket;

public class RSendUDP implements RSendUDPI {
	private int mode = 0;
	private long windowSize = 256;
	private int localPort = 12987;
	private String filename = "";
	private InetAddress inet = InetAddress.getLocalHost();
	private InetSocketAddress receiver = new InetSocketAddress(inet, 12988);
	private long timeout = 1000;

	public RSendUDP() throws UnknownHostException {

	}

	// returns file name
	public String getFilename() {
		return filename;
	}

	// returns local port number
	public int getLocalPort() {
		return localPort;
	}

	/*
	 * Returns an int indicating mode of operation
	 */
	public int getMode() {
		return mode;
	}

	// returns mode parameter
	public long getModeParameter() {
		return windowSize;
	}

	public InetSocketAddress getReceiver() {
		return receiver;
	}

	public long getTimeout() {
		return timeout;
	}

	/*
	 * initiates file transmission returns true if successful!
	 */
	public boolean sendFile() {

		if (mode == 0) {
			//Print an initial message indicating Local IP, ARQ Alg, UDP sending to
			System.out.println("Sending " + filename + " on local port : " + localPort + " to address: "+ " on port: " + "using stop-and-wait algorithm");
			
			UDPSocket socket;
			int mtu;
			try {
				// socket that will send message to receiver
				socket = new UDPSocket(localPort);
				System.out.println("Sending from localPort: " + localPort);
				mtu = socket.getSendBufferSize();
				if (mtu == -1) {
					mtu = Integer.MAX_VALUE;
				}

			} catch (Exception e) {
				System.out.println("Error: Initial connection to localPort: " +localPort + " failed");
				System.err.println(e);
				return false;
			}
			File file = new File("./" + filename);
			if (!file.exists()) {
				System.out.println("File Not Found");
				return false;
			}
			else {
				System.out.println("Successfully found file");
			}
			long fileLength = file.length();

			FileInputStream fis;
			/*
			 * Build header Seq. # 1 byte, more or eof, port to respond to 2 Bytes (acksocket)?, IP to
			 * respond to? Could be grabbed from socket datagram I believe
			 */
			byte[] header = new byte[3];
			byte[] message;
			byte[] transfer;
			try {
				fis = new FileInputStream(file);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			int filePointer = 0;
			int readSize = mtu - header.length;

			int sequenceNum = 0;
			int dataRead = 0;
			while (filePointer < fileLength) {
				// builds header! more or eof
				header[0] = (byte) (sequenceNum & 0xFF);
				header[1] = (byte) ((sequenceNum >> 8) & 0xFF);
				
				// get message data from file using fis
				try {
					if ((filePointer + readSize) <= fileLength) {
						message = new byte[readSize];
						dataRead = fis.read(message, 0, readSize);
						filePointer += readSize;
						header[2] = (byte) 255;//signifies more messages are coming
					}
					else {
						message = new byte[(int) (fileLength-filePointer)];
						dataRead = fis.read(message, 0, (int) fileLength-filePointer);
						filePointer = (int) fileLength;
						header[2] = (byte) 0;//signifies last message transmitted
					}
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
				
				//create the concatenated header + payload to send
				transfer = concat(header, message);
				try {
					socket.send(new DatagramPacket(transfer, transfer.length, receiver.getAddress(), receiver.getPort()));
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				//wait for an ack
				try {
					byte[] ack = new byte[2];
					DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
					TimerTask task = 
					Timer timer = new Timer();
					socket.receive(ackPacket);
					int ackSeqNum = ((ack[1] & 0xff) << 8) | (ack[0] & 0xff);
					System.out.println("Ack for " + ackSeqNum);
				} catch (IOException e) {
					e.printStackTrace();
				}
				sequenceNum++;
			}
			
		} else if (mode == 1) {
			System.out.println("Sending " + filename + " on local port : " + localPort + " to address: "+ " on port: " + "using sliding-window algorithm");
			UDPSocket socket;
			long maxSeqNum;
			try {
				// socket that will send message to receiver
				socket = new UDPSocket(localPort);
				int mtu = socket.getSendBufferSize();
				maxSeqNum = windowSize / mtu;
			} catch (Exception e) {
				System.err.println(e);
			}
			File file = new File("./" + filename);
			try {
				FileInputStream fis = new FileInputStream(file);
			} catch (IOException e) {
				e.printStackTrace();
			}
			/*
			 * Build header Seq. # 1 byte, port to respond to 2 Bytes (acksocket), IP to
			 * respond to
			 */
		} else {
			System.out.println("Error: Mode does not exist");
		}


		return true;
	}
	
	private byte[] concat(byte[] head, byte[] body) {
		   int headLen = head.length;
		   int bodyLen = body.length;
		   byte[] c= new byte[headLen+bodyLen];
		   System.arraycopy(head, 0, c, 0, headLen);
		   System.arraycopy(body, 0, c, headLen, bodyLen);
		   return c;
		}

	/*
	 * Sets name of file that should be sent
	 */
	public void setFilename(String arg0) {
		filename = arg0;
	}

	/*
	 * Indicates the local port number used by the host Defaults to 12987
	 */
	public boolean setLocalPort(int arg0) {
		boolean success = true;
		localPort = arg0;
		return success;
	}

	/*
	 * Specifies the algorithm for reliable delivery where the mode is 0 or 1 Mode 0
	 * = stop-and-wait; Mode 1 = sliding window; Default's to 0
	 */
	public boolean setMode(int arg0) {
		mode = arg0;
		return true;
	}

	/*
	 * Used to indicate the size of the window in bytes for the sliding window mode
	 * Has no effect if using stop-and-wait Default's to 256 (use this val and MTU
	 * (max payload size) value to calculate Max# of outstanding frames you can send
	 * using sliding window algorithm
	 */
	public boolean setModeParameter(long arg0) {
		windowSize = arg0;
		return false;
	}

	/*
	 * Specifies IP address of the receiver and the remote port number Defaults to
	 * Local host
	 */
	public boolean setReceiver(InetSocketAddress arg0) {
		receiver = arg0;
		return true;
	}

	/*
	 * Specifies the timeout value in milliseconds default value should be one
	 * second 1000 ms
	 */
	public boolean setTimeout(long arg0) {
		timeout = arg0;
		return true;
	}
}
