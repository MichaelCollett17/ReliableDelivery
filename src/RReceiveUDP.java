import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

/*
 * Problems to fix:
 *  1. Build file
 */

public class RReceiveUDP implements RReceiveUDPI {
	private String filename = "";
	private int localPort = 12987;
	private int mode = 0;
	private long windowSize = 256;
	File outputFile;
	FileOutputStream file;
	final static int headerLength = 3;

	/*
	 * Initiates file reception returns true if successful
	 */
	public boolean receiveFile() {
		try {
			outputFile = new File(filename);
			System.out.println("filename: " +filename);
			outputFile.createNewFile();
			file = new FileOutputStream(outputFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		System.out.println("----------------Initiate receiveFile()-----------------");

		if (mode == 0) {
			System.out.println("### Receiving on local port: " + localPort + " using stop-and-wait algorithm ###");
			UDPSocket socket = null;
			int mtu = 0;
			byte[] buffer;
			boolean morePackets = true;
			int lastRec = 0;

			try {
				socket = new UDPSocket(localPort);
				mtu = socket.getSendBufferSize();

			} catch (SocketException e) {
				e.printStackTrace();
			}

			while (morePackets) {
				try {
					buffer = new byte[mtu];
					DatagramPacket readPacket = new DatagramPacket(buffer, buffer.length);
					socket.receive(readPacket);
					InetAddress clientAddress = readPacket.getAddress();
					int clientPort = readPacket.getPort();
					int seqNum = ((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff);
					int moreIndicator = (int) buffer[2];
					System.out.println("### Received packet from " + readPacket.getAddress().getHostAddress()
							+ " with sender port: " + readPacket.getPort() + ", sequence number: " + seqNum
							+ ", and data: " + (buffer.length - 3) + " ###");
					if(lastRec == seqNum) {
						file.write(buffer,3,buffer.length-3);
						lastRec++;
					}
					/*
					 * Send ACK
					 */
					byte[] ack = new byte[2];
					ack[0] = buffer[0];
					ack[1] = buffer[1];
					socket.send(new DatagramPacket(ack, ack.length,
							InetAddress.getByName(readPacket.getAddress().getHostAddress()), readPacket.getPort()));
					if (moreIndicator == 0) {
						System.out.println("### Received last packet ###");
						morePackets = false;
						System.out.println("----------------End receiveFile()-----------------");
						AtomicBoolean lastAckNotReceived = new AtomicBoolean(false);
						LastAckCheck lastAckCheck = new LastAckCheck(readPacket, socket, lastAckNotReceived);
						Thread lastAckCheckThread = new Thread(lastAckCheck);
						lastAckCheckThread.start();
						long timeFromNow = System.currentTimeMillis() + 3000;
						while (System.currentTimeMillis() < timeFromNow) {
						}
						if (lastAckNotReceived.get()) {
							morePackets = true;
						} else {
							lastAckNotReceived.set(true);
							return true;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
		} else if (mode == 1) {
			System.out.println("Receiving " + filename + " on local IP: " + "  and local port : " + localPort
					+ "using sliding-window algorithm");
		} else {
			System.out.println("Mode does not exist");
			return false;
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

	/*
	 * Sets received file name
	 */
	public void setFilename(String arg0) {
		filename = arg0;
	}

	/*
	 * Indicates the local port number used by the host Defaults to 12987
	 */
	public boolean setLocalPort(int arg0) {
		localPort = arg0;
		return true;
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
}

class LastAckCheck implements Runnable {
	DatagramPacket readPacket;
	UDPSocket socket;
	AtomicBoolean lastAckNotReceived;

	public LastAckCheck(DatagramPacket dp, UDPSocket s, AtomicBoolean lanr) {
		readPacket = dp;
		socket = s;
		lastAckNotReceived = lanr;
	}

	public void run() {
		try {
			while (!lastAckNotReceived.get()) {
				socket.receive(readPacket);
				lastAckNotReceived.set(true);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}