import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import edu.utulsa.unet.UDPSocket;

public class TestUDP {
	public static void main(String[] args) {
		RSendUDP sender;
		try {
			sender = new RSendUDP();
			sender.setMode(0);
			sender.setLocalPort(12988);
			sender.setFilename("meow.txt");
			sender.setReceiver( new InetSocketAddress(InetAddress.getLocalHost(), 12986));
			sender.sendFile();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
}
