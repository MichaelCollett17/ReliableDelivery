
public class TestRec {
	public static void main(String [] args) {
		RReceiveUDP receiver = new RReceiveUDP();
		receiver.setLocalPort(12986);
		receiver.setMode(0);
		receiver.setModeParameter(1024);
		receiver.setFilename("C:\\Users\\Michael Collett\\eclipse-workspace\\ReliableDelivery\\src\\received.txt");
		receiver.receiveFile();
	}
}
