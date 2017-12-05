import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

/*
 * Problems to fix:
 */

public class RReceiveUDP implements RReceiveUDPI {
	private String filename = "";
	private int localPort = 12987;
	private int mode = 0;
	private long windowSize = 256;
	final static int headerLength = 3;
	private UDPSocket socket;
	private int mtu;
	private ArrayList<Frame> outstandingFrames = new ArrayList<Frame>();
	private int clientPort;
	private InetAddress clientAddress;
	private long lastFrameReceived = -1;
	private long maxOutstandingFrames;
	private long lastAcceptableFrame;

	/*
	 * Initiates file reception returns true if successful
	 */
	public boolean receiveFile() {
		File outputFile;
		FileOutputStream file = null;
		try {
			socket = new UDPSocket(localPort);
			outputFile = new File(filename);
			outputFile.createNewFile();
			file = new FileOutputStream(outputFile);
			mtu = socket.getSendBufferSize();
			if (mtu == -1) {
				mtu = Integer.MAX_VALUE;
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		byte[] buffer = new byte[mtu];
		DatagramPacket readPacket = new DatagramPacket(buffer, buffer.length);
		long maxOutstandingFrames = windowSize / mtu;
		lastAcceptableFrame = maxOutstandingFrames;
		boolean morePackets = true;
		boolean first = true;

		System.out.println("----------------Initiate receiveFile()-----------------");

		if (mode == 0) {
			System.out.println("### Receiving on local port: " + localPort + " using stop-and-wait algorithm ###");
		} else if (mode == 1) {
			System.out.println("### Receiving on local port: " + localPort + " using sliding-window algorithm ###");
		} else {
			System.out.println("Mode does not exist");
			return false;
		}

		while (morePackets) {
			try {
				socket.receive(readPacket);
				if (first) {
					clientAddress = readPacket.getAddress();
					clientPort = readPacket.getPort();
					System.out.println(
							"### Connection established with " + clientAddress + " on port " + clientPort + " ###");
				}
				int seqNum = ((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff);
				int moreIndicator = (int) buffer[2];
				System.out.println("### Received packet from " + readPacket.getAddress().getHostAddress()
						+ " with sender port: " + readPacket.getPort() + ", sequence number: " + seqNum + ", and data: "
						+ (buffer.length - 3) + " ###");
				if (lastFrameReceived + 1 == seqNum) {
					file.write(buffer, 3, buffer.length - 3);
					lastFrameReceived++;
					lastAcceptableFrame++;
					checkWindow();
				} else if ((seqNum > lastFrameReceived) && (seqNum <= lastAcceptableFrame) && mode == 1) {
					saveFrame(buffer, seqNum);// modulus operator on loop for purging frames
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
						System.out.println("----------------End receiveFile()-----------------");
						return true;
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
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

	public void saveFrame(byte[] buffer, int seqNum) {
		Frame frame = new Frame(buffer, seqNum);
		outstandingFrames.add(frame);
	}

	public void checkWindow() {
		for (int idx = 0; idx < outstandingFrames.size(); idx++) {
			Frame temp = outstandingFrames.get(idx);
			if (temp.getSeqNum() <= lastFrameReceived) {
				outstandingFrames.remove(idx);
			} else if (temp.getSeqNum() == (lastFrameReceived + 1)) {
				// write and ack increment lfr and laf and recursive call8
				byte[] buffer = temp.getBuffer();
				byte[] ack = new byte[2];
				ack[0] = buffer[0];
				ack[1] = buffer[1];
				try {
					socket.send(new DatagramPacket(ack, ack.length, clientAddress, clientPort));
				} catch (IOException e) {
					e.printStackTrace();
				}
				lastFrameReceived++;
				lastAcceptableFrame++;
			}
		}
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

class Frame {
	private byte[] buffer;
	private int seqNum;

	public byte[] getBuffer() {
		return buffer;
	}

	public void setBuffer(byte[] buffer) {
		this.buffer = buffer;
	}

	public int getSeqNum() {
		return seqNum;
	}

	public void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
	}

	public Frame(byte[] b, int sn) {
		buffer = b;
		seqNum = sn;
	}
}