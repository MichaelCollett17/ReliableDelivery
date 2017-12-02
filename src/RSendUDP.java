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
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import edu.utulsa.unet.RSendUDPI;
import edu.utulsa.unet.UDPSocket;

/*
 * Problems to fix:
 *  1. Continuous transmission of last piece kind of fixed
 */

public class RSendUDP implements RSendUDPI {
	private int mode = 0;
	private long windowSize = 256;
	private int localPort = 12987;
	private String filename = "";
	private InetAddress inet = InetAddress.getLocalHost();
	private InetSocketAddress receiver = new InetSocketAddress(inet, 12987);
	private long timeout = 1000;
	private UDPSocket socket;
	private int mtu;

	public RSendUDP() throws UnknownHostException {
	}

	/*
	 * initiates file transmission returns true if successful
	 */
	public boolean sendFile() {
		System.out.println("----------------Initiate sendFile()-----------------");

		/*
		 * Initialize Socket and MTU
		 */
		try {
			socket = new UDPSocket(localPort);
			mtu = socket.getSendBufferSize();
			if (mtu == -1) {
				mtu = Integer.MAX_VALUE;
			}

		} catch (Exception e) {
			System.out.println("Error: Initial connection to localPort: " + localPort + " failed");
			System.err.println(e);
			return false;
		}

		if (mode == 0) {
			System.out.println("### Sending " + filename + " on local port : " + localPort + " to address: "
					+ " on port: " + "using stop-and-wait algorithm ###");

			File file = new File(filename);
			if (!file.exists()) {
				System.out.println("File Not Found");
				return false;
			} else {
				System.out.println("Successfully found file");
			}
			long fileLength = file.length();
			FileInputStream fis;
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
						header[2] = (byte) 255;// signifies more messages are coming
					} else {
						message = new byte[(int) (fileLength - filePointer)];
						dataRead = fis.read(message, 0, (int) fileLength - filePointer);
						filePointer = (int) fileLength;
						header[2] = (byte) 0;// signifies last message transmitted
					}
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
				transfer = concat(header, message);
				try {
					AtomicBoolean ackNotReceived = new AtomicBoolean(true);
					byte[] ack = new byte[2];
					DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
					Retransmit retransmit = new Retransmit();
					retransmit.setAckNotReceived(ackNotReceived);
					retransmit.setReceiver(receiver);
					retransmit.setSocket(socket);
					retransmit.setTimeout(timeout);
					retransmit.setTransfer(transfer);
					Thread retransThread = new Thread(retransmit);
					socket.send(
							new DatagramPacket(transfer, transfer.length, receiver.getAddress(), receiver.getPort()));
					retransThread.start();
					socket.receive(ackPacket);
					int ackSeqNum = ((ack[1] & 0xff) << 8) | (ack[0] & 0xff);
					System.out.println("### Ack received for sequence number: " + ackSeqNum + " ###");
					ackNotReceived.set(false);
				} catch (IOException e) {
					e.printStackTrace();
				}
				sequenceNum++;
			}

		} else if (mode == 1) {
			System.out.println("Sending " + filename + " on local port : " + localPort + " to address: " + " on port: "
					+ "using sliding-window algorithm");
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

	private byte[] concat(byte[] head, byte[] body) {
		int headLen = head.length;
		int bodyLen = body.length;
		byte[] c = new byte[headLen + bodyLen];
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

class Retransmit implements Runnable {
	public UDPSocket socket;
	public byte[] transfer;
	public InetSocketAddress receiver;
	public AtomicBoolean ackNotReceived;
	public long timeout;

	public void setTimeout(long timeout2) {
		timeout = timeout2;
	}

	public void setAckNotReceived(AtomicBoolean aNR) {
		ackNotReceived = aNR;
	}

	public void setSocket(UDPSocket s) {
		socket = s;
	}

	public void setTransfer(byte[] t) {
		transfer = t;
	}

	public void setReceiver(InetSocketAddress r) {
		receiver = r;
	}

	public void run() {
		long timeOfTransmit = System.currentTimeMillis();
		int seqNum = ((transfer[1] & 0xff) << 8) | (transfer[0] & 0xff);
		while (ackNotReceived.get()) {
			if (timeOfTransmit + timeout < System.currentTimeMillis()) {
				System.out.println("### Retransmitting Packet With Sequence Number: " + seqNum + " ###");
				try {
						socket.send(new DatagramPacket(transfer, transfer.length, receiver.getAddress(),
								receiver.getPort()));
						timeOfTransmit = System.currentTimeMillis();
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return;
	}
}
