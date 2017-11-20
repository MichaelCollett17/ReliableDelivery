
public class TestRec {
	public static void main(String [] args) {
		RReceiveUDP receiver = new RReceiveUDP();
		receiver.setMode(0);
		receiver.receiveFile();
	}
}
