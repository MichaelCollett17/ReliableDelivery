import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import edu.utulsa.unet.UDPSocket;

public class TestUDP {
	public static void main(String[] args) {
		RSendUDP sender;
		try {
			sender = new RSendUDP();
			sender.setMode(0);
			sender.setFilename("meow.txt");
			sender.sendFile();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

	}

	public static void meow() {

		int maxSeqNumMin1Div2 = 4;
		int maxPacketSizeBytes = 8;
		int messagePointer = 0;
		int bytesOfMessageSize = maxPacketSizeBytes - 1;// 1 becoming the eventualsize of header
		try {
			UDPSocket socket = new UDPSocket(23456);
			UDPSocket ackSocket = new UDPSocket(23457);

			byte[] message = ("Hello motherfucking meow meow meow meow meow emwerjlaskjdf;laksjdf;lkasjdf;lkasjdfl;kjasfjalksdjf;lakjsdf;lkasjdf;lkjdfajkfasdk;jlfasdjlk;fasdkjl;fadsjk;ladsfk;jlfasdlk;jfasdlkj;afsdlkj;fasdioewroietrwnjagfsdlk;njvad;nlkvds;hnjfds;jlkhasdfjlk World testing reliable delivery. Making this message extra long so that I can test parsing large packets into smaller packets. these are useless words. Life is without purpose")
					.getBytes();

			for (int idx = 0; idx < maxSeqNumMin1Div2; idx++) {
				byte seqNum = (byte) idx;
				byte[] transmit = new byte[maxPacketSizeBytes];
				transmit[0] = seqNum;
				System.out.println(idx);
				System.out.println("bmp " + messagePointer);
				System.out.println("boms " + bytesOfMessageSize);
				System.out.println("ml " + message.length);
				if ((messagePointer + bytesOfMessageSize) <= message.length) {
					transmit = java.util.Arrays.copyOfRange(message, messagePointer,
							(bytesOfMessageSize + messagePointer));
					messagePointer += bytesOfMessageSize;
					System.out.println("Step" + (bytesOfMessageSize + messagePointer));
				} else {
					transmit = java.util.Arrays.copyOfRange(message, messagePointer, message.length);
					messagePointer = message.length;
					System.out.print("stop");
				}
				int localPort = 0;//ignore
				socket.send(
						new DatagramPacket(transmit, transmit.length, InetAddress.getByName("127.0.0.1"), localPort));
				byte[] ack = new byte[1];
				DatagramPacket packet = new DatagramPacket(ack, ack.length);
				ackSocket.receive(packet);
				InetAddress client = packet.getAddress();
				System.out.println(" Received " + new String(ack) + " from " + packet.getAddress().getHostAddress()
						+ " with sender port " + packet.getPort());
				if (messagePointer >= message.length) {
					System.out.println("Break");
					break;
				}
				if (maxSeqNumMin1Div2 == idx + 1) {
					idx = 0;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
