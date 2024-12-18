package pt.ulisboa.tecnico.sec.client;
import java.security.NoSuchAlgorithmException;

public class ClientMain {
	public static void main(String[] args) {
		final String serverHost = "localhost";
		final int serverBasePort = 8000;
		int numServers = 3;
		int numFaults = 1;

		System.out.println(">>> " + ClientMain.class.getSimpleName() + " <<<");

		if (args.length == 2) {
			numServers = Integer.parseInt(args[0]);
			numFaults = Integer.parseInt(args[1]);
		}
		Client client = new Client(serverHost, serverBasePort, numServers, numFaults);
		try {
			client.start();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
}