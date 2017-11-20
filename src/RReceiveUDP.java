import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;

import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

public class RReceiveUDP implements RReceiveUDPI {
	private String filename = "";
	private int localPort = 12988;
	private int mode = 0;
	private long modeParameter = 256;

	final static int headerLength = 3;

	public static void main(String[] args) {

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
		return modeParameter;
	}

	/*
	 * Initiates file reception Returns true if successful
	 */
	public boolean receiveFile() {
		if (mode == 0) {
			// print initial message
			System.out.println("Receiving on local port: " + localPort
					+ " using stop-and-wait algorithm");
			UDPSocket socket = null;
			int mtu = 0;
			byte[] buffer;
			boolean morePackets = true;

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
					System.out.println("Received packet from " + readPacket.getAddress().getHostAddress()
							+ " with sender port: " + readPacket.getPort() + ", sequence number: " + seqNum + ", and data: "+ (buffer.length -3));
					System.out.println("MEOW: " + new String(buffer));
					
					//send ack
					byte[] ack = new byte[2];
					ack[0] = buffer[0];
					ack[1] = buffer[1];
					socket.send(new DatagramPacket(ack, ack.length, InetAddress.getByName(readPacket.getAddress().getHostAddress()), readPacket.getPort()));
					if (moreIndicator == 0) {
						System.out.println("Received last packet");
						morePackets = false;
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

		// UDPSocket respond = new UDPSocket(12345);

		// while (true) {
		// int mtu;
		// socket.getSendBufferSize();
		// byte[] buffer = new byte[64];
		// DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		// socket.receive(packet);// blocking reception
		// InetAddress client = packet.getAddress();
		// System.out.println(" Received " + new String(buffer) + " from " +
		// packet.getAddress().getHostAddress()
		// + " with sender port " + packet.getPort());

		// byte[] ack = java.util.Arrays.copyOfRange(buffer, 0, 1);
		// respond.send(new DatagramPacket(ack, ack.length,
		// InetAddress.getByName(packet.getAddress().getHostAddress()), 23457));

		// }
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
		modeParameter = arg0;
		return false;
	}

}
