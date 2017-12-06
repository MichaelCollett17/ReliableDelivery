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
 * 1. Special 128 byte to signify completion and return true and print end file
 * 2. header ish
 */

public class RReceiveUDP implements RReceiveUDPI {
	private String filename = "";
	private int localPort = 12987;
	private int mode = 0;
	private long windowSize = 256;
	final static int headerLength = 3;
	private UDPSocket socket;
	private int mtu;
	private long data = 0;
	private ArrayList<Frame> outstandingFrames = new ArrayList<Frame>();
	private int clientPort;
	private InetAddress clientAddress;
	private long lastFrameReceived = -1;
	private long maxOutstandingFrames;
	private long lastAcceptableFrame;
	private File outputFile;
	private FileOutputStream file = null;
	/*
	 * Initiates file reception returns true if successful
	 */
	public boolean receiveFile() {

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
		
		long maxOutstandingFrames = windowSize / mtu;
		lastAcceptableFrame = maxOutstandingFrames;
		boolean morePackets = true;
		boolean first = true;
		long lastPacket = -100;

		System.out.println("----------------Initiate receiveFile()-----------------");

		if (mode == 0) {
			System.out.println("### Receiving on local port: " + localPort + " using stop-and-wait algorithm ###");
		} else if (mode == 1) {
			System.out.println("### Receiving on local port: " + localPort + " using sliding-window algorithm ###");
		} else {
			System.out.println("Mode does not exist");
			return false;
		}

		while (morePackets && ((lastFrameReceived != lastPacket) || (mode==0))) {
			try {
				byte[] buffer = new byte[mtu];
				DatagramPacket readPacket = new DatagramPacket(buffer, buffer.length);
				socket.receive(readPacket);
				if (first) {
					clientAddress = readPacket.getAddress();
					clientPort = readPacket.getPort();
					System.out.println(
							"### Connection established with " + clientAddress + " on port " + clientPort + " ###");
					first = false;
				}
				int seqNum = ((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff);
				int moreIndicator = (int) buffer[2];
				if (moreIndicator == 0) {
					lastPacket = seqNum;
				}
				System.out.println("### Received packet from " + readPacket.getAddress().getHostAddress()
						+ " with sender port: " + readPacket.getPort() + ", sequence number: " + seqNum + ", and data: "
						+ (buffer.length - 3) + " ###");
				if (seqNum < lastFrameReceived + 1) {
					byte[] ack = new byte[2];
					ack[0] = buffer[0];
					ack[1] = buffer[1];
					socket.send(new DatagramPacket(ack, ack.length,
							InetAddress.getByName(readPacket.getAddress().getHostAddress()), readPacket.getPort()));
				}
				else if (seqNum == lastFrameReceived + 1) {
					System.out.println("LINE 90 IF STATEMENT");
					file.write(buffer, 3, buffer.length - 3);
					System.out.println("Writing seqNum: " + seqNum);
					data += buffer.length-3;
					byte[] ack = new byte[2];
					ack[0] = buffer[0];
					ack[1] = buffer[1];
					socket.send(new DatagramPacket(ack, ack.length,
							InetAddress.getByName(readPacket.getAddress().getHostAddress()), readPacket.getPort()));
					lastFrameReceived++;
					lastAcceptableFrame++;
					checkWindow();
				} else if ((seqNum > lastFrameReceived + 1) && seqNum <= (lastFrameReceived + maxOutstandingFrames) && mode == 1) {
					saveFrame(buffer, seqNum);// modulus operator on loop for purging frames
				}
				
				if (moreIndicator == 0 && mode==0) {
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
						//lastAckNotReceived.set(true);
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

	public void saveFrame(byte[] buffer, int seqNum) {
		Frame frame = new Frame(buffer, seqNum);
		System.out.println("SAVE FRAME \n SeqNum: " + seqNum +"\n" + "Buffer: " + new String(buffer));
		System.out.println("SAVE FRAME \n SeqNumba: " + frame.getSeqNum() +"\n" + "Bufferba: " + new String(frame.getBuffer()));
		outstandingFrames.add(frame);
	}

	public void checkWindow() {
		ArrayList<Frame> toBeRemoved = new ArrayList<Frame>();
		for (Frame f: outstandingFrames) {
			if (f.getSeqNum() <= lastFrameReceived) {
				byte[] buffer = f.getBuffer();
				byte[] ack = new byte[2];
				ack[0] = buffer[0];
				ack[1] = buffer[1];
				toBeRemoved.add(f);
				try {
					socket.send(new DatagramPacket(ack, ack.length, clientAddress, clientPort));
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (f.getSeqNum() == (lastFrameReceived + 1)) {
				byte[] buffer = f.getBuffer();
				byte[] ack = new byte[2];
				ack[0] = buffer[0];
				ack[1] = buffer[1];
				int acksn = ((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff);
				System.out.println("ack: " + acksn);
				System.out.println("Received " + new String(buffer));
				try {
					file.write(buffer, 3, buffer.length - 3);
					System.out.println("Writing for Sequence #: " + f.getSeqNum());
					socket.send(new DatagramPacket(ack, ack.length, clientAddress, clientPort));
				} catch (IOException e) {
					e.printStackTrace();
				}
				lastFrameReceived++;
				lastAcceptableFrame++;
				toBeRemoved.add(f);
			}
		}
		outstandingFrames.removeAll(toBeRemoved);
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