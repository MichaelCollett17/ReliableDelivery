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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
	private int mtu = 0;
	private long lastAckReceived, lastFrameSent = -1;
	private long maxOutstandingFrames;
	private List<RetransmitRecord> records = Collections.synchronizedList(new ArrayList<RetransmitRecord>());
	private InetAddress serverAddress;
	private int serverPort;

	public RSendUDP() throws UnknownHostException {
	}

	/*
	 * initiates file transmission returns true if successful
	 */
	public boolean sendFile() {
		File file;
		long fileLength;
		FileInputStream fis;
		byte[] header = new byte[3];
		byte[] message;
		byte[] transfer;
		boolean first = true;
		System.out.println("----------------Initiate sendFile()-----------------");

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
		maxOutstandingFrames = windowSize / mtu;

		file = new File(filename);
		if (!file.exists()) {
			System.out.println("File Not Found");
			return false;
		} else {
			System.out.println("Successfully found file");
		}
		fileLength = file.length();
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
		AtomicBoolean doneReceiving = new AtomicBoolean(false);
		Receiver r = null;
		if (mode == 0) {
			System.out.println("### Sending " + filename + " on local port : " + localPort + " to address: "
					+ " on port: " + "using stop-and-wait algorithm ###");
		} else if (mode == 1) {
			System.out.println("Sending " + filename + " on local port : " + localPort + " to address: " + " on port: "
					+ "using sliding-window algorithm");
			r = new Receiver(records, doneReceiving, socket);
			Thread receiverThread = new Thread(r);
			receiverThread.start();
		} else {
			System.out.println("Error: Mode does not exist");
			return false;
		}
		while (filePointer < fileLength ) {
			if ((lastFrameSent - lastAckReceived) < maxOutstandingFrames) {
				header[0] = (byte) (sequenceNum & 0xFF);
				header[1] = (byte) ((sequenceNum >> 8) & 0xFF);
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
					if (mode == 0) {
						byte[] ack = new byte[2];
						DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
						socket.receive(ackPacket);
						if (first) {
							serverAddress = ackPacket.getAddress();
							serverPort = ackPacket.getPort();
							System.out.println("### Connection established with " + serverAddress + " on port "
									+ serverPort + " ###");
							first = false;
						}
						int ackSeqNum = ((ack[1] & 0xff) << 8) | (ack[0] & 0xff);
						System.out.println("### Ack received for sequence number: " + ackSeqNum + " ###");
						ackNotReceived.set(false);
					}
					if (mode == 1) {
						RetransmitRecord record = new RetransmitRecord(ackNotReceived, sequenceNum);
						records.add(record);
						/*
						 * create new thread that receives and updates lar and lfs and kills the thread
						 * that is retransmitting via an arraylist of atomic booleans... this thread
						 * might just want to be run once.
						 */

					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				sequenceNum++;
			} // end of large if
		}

		System.out.println("----------------End sendFile()-----------------");
		doneReceiving.set(true);
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

class Receiver implements Runnable {
	public List<RetransmitRecord> records;
	public AtomicBoolean done;
	public UDPSocket socket;

	public Receiver(List<RetransmitRecord> r, AtomicBoolean d, UDPSocket s) {
		records = r;
		done = d;
		socket = s;
	}

	public void run() {
		while (!done.get()) {
			byte[] ack = new byte[2];
			DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
			try {
				socket.receive(ackPacket);
			} catch (IOException e) {
				e.printStackTrace();
			}
			int ackSeqNum = ((ack[1] & 0xff) << 8) | (ack[0] & 0xff);
			System.out.println("### Ack received for sequence number: " + ackSeqNum + " ###");
			setFalse(ackSeqNum);
		}
	}

	public void setFalse(int sequenceNumber) {
		for (int idx =0; idx < records.size(); idx++) {
			if (records.get(idx).getSeqNum() == sequenceNumber) {
				records.get(idx).getAckNotReceived().set(false);
				records.remove(idx);
			}
		}
	}
}

class RetransmitRecord {
	private AtomicBoolean ackNotReceived;
	private int seqNum;

	public AtomicBoolean getAckNotReceived() {
		return ackNotReceived;
	}

	public void setAckNotReceived(AtomicBoolean ackNotReceived) {
		this.ackNotReceived = ackNotReceived;
	}

	public int getSeqNum() {
		return seqNum;
	}

	public void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
	}

	public RetransmitRecord(AtomicBoolean anr, int sn) {
		ackNotReceived = anr;
		seqNum = sn;
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
					socket.send(
							new DatagramPacket(transfer, transfer.length, receiver.getAddress(), receiver.getPort()));
					timeOfTransmit = System.currentTimeMillis();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return;
	}
}
