package pt.ulisboa.tecnico.sec.server;

import java.io.IOException;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import java.util.Scanner;

import java.security.*;

public class ServerMain {
	private static Server server = null;
	private static ServerServiceImpl impl = null;
	private static final int baseport = 8000;
	private static int port = 8000;
	private static int numServers = 3;
	private static int numFaults = 1;
	private static int id = 0;

	private static SignalHandler handler;

	public static void setSignalHandler() {
		handler = new SignalHandler() {
			public void handle(Signal sig) {
				server.shutdown();
			}
		};

		Signal.handle(new Signal("INT"), handler);
		Signal.handle(new Signal("TERM"), handler);
	}

	public static void main(String[] args) {
		int basePort = 8000;
		int numServers = 1;
		int numFaults = 0;
		Scanner scanner = new Scanner(System.in);

		if (args.length == 3) {
			id = Integer.parseInt(args[0]);
			port = basePort + id;
			numServers = Integer.parseInt(args[1]);
			numFaults = Integer.parseInt(args[2]);
		}

		System.out.println(">>> " + ServerMain.class.getSimpleName() + " (id= " + id + ") <<<");

		try {
			impl = new ServerServiceImpl(port, basePort, numServers, numFaults, id);
			server = ServerBuilder.forPort(port).addService(impl).build();
			server.start();
			System.out.println("Press Enter ONLY WHEN all servers are up and running to gather publicKeys");
			String input = scanner.nextLine();
			impl.populateKeys();
			System.out.println("Ready!\nListening on port " + port + "...");

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted.");
		} catch (IOException | ClassNotFoundException e) {
			System.out.println("ERROR: Could not start server.");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("ERROR: Could not start server due to error in key pairs.");
		} finally {
			impl.shutdown();
			server.shutdown();
		}
	}
}
