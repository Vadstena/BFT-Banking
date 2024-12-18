package pt.ulisboa.tecnico.sec.client;

import pt.ulisboa.tecnico.sec.server.grpc.ServerServiceGrpc;

import io.grpc.StatusRuntimeException;
import java.io.File;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKey;
import java.util.Scanner;
import java.util.HashMap;

import pt.ulisboa.tecnico.sec.crypto.Crypto;

public class Client {
    private boolean logged = false;
    private String serverHost;
    private int serverBasePort;
    private int numServers;
    private int numFaults;

    public Client(String serverHost, int serverBasePort, int numServers, int numFaults) {
        this.serverHost = serverHost;
        this.serverBasePort = serverBasePort;
        this.numServers = numServers;
        this.numFaults = numFaults;
    }

    private KeyStore ks;
    private PublicKey pubKey;
    private PrivateKey privKey;
    private String username;
    private String passwd;
    private String hashedpwd;
    private String keypwd;


	public void login() {

        Scanner scanner = new Scanner(System.in);

		System.out.println("\n= LOGIN =\nPlease insert username:");
		username = scanner.nextLine();


		try {
			username = Crypto.hashMyKey(username);
            File file= new File("tmp/accounts/"+username+".txt");
            boolean exists = file.exists();

            if(!exists){
                System.out.println("\nUsername not found.");
            }
            else {
				System.out.println("\nPlease insert password:");
				passwd = scanner.nextLine();
                try {
                    Scanner fr = new Scanner(file);
                    hashedpwd = fr.next();
                    if (hashedpwd.equals(Crypto.hashMyKey(passwd))) {
                        System.out.println("LOGGED IN");
                        this.logged=true;
                        this.keypwd = username+passwd;
                        loadKeys();
                    }
                    else {
                        System.out.println("\nIncorrect password, please try again.");
                    }
                } catch (Exception e3) {
                }
            }

		}
		catch (Exception e) {
		  System.out.println("An error occurred.");
		  e.printStackTrace();
		}
	}

    public void register(){
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n= REGISTER =\nPlease insert username:");
        username = scanner.nextLine();

        System.out.println("\nPlease insert password:");
        passwd = scanner.nextLine();

        try {
	    username = Crypto.hashMyKey(username);
            KeyPair keyPair = Crypto.generateKeyPair();

            File file= new File("tmp/accounts/" + username + ".txt");
            boolean exists = file.exists();
            if(!exists){
                try {
                    FileWriter fw = new FileWriter(file);
                    file.createNewFile();
                    hashedpwd = Crypto.hashMyKey(passwd);
                    fw.write(hashedpwd);
                    fw.close();
                    this.keypwd = username+passwd;
                    this.logged = true;
                    this.pubKey = keyPair.getPublic();
                    this.privKey = keyPair.getPrivate();

                    storeKeys();

                    System.out.println("\nRegistered successfully!");
                }
				catch(Exception e1){
					System.out.println(e1);
				}
            }
            else {
                System.out.println("Username already taken. Please, choose other one.");
            }

        }
        catch (Exception e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }
    public void start() throws NoSuchAlgorithmException {
        ClientAPI.init(this.serverHost, this.serverBasePort, this.numServers, this.numFaults);



        String input;
        Scanner scanner = new Scanner(System.in);
        while(true) {
			if(!logged){
				System.out.println("\nPlease select one option:\n" +
						"1. Login\n" +
						"2. Register\n" +
						"0. Exit");
						System.out.print("> ");
			
				input = scanner.nextLine();
				switch(input) {
					case "1":
						login();
						continue;
					case "2":
						register();
						continue;
					case "0":
						scanner.close();
						System.out.println("Exiting client...");
                        ClientAPI.close();
						return;
					default:
						System.out.println("Possible commands: 'test'");
						continue;
					
				}
			}
			else{
				System.out.println("\nPlease select one option:\n" +
						"1. Open Account\n" +
						"2. Send Amount\n" +
						"3. Check Account\n" +
						"4. Receive Amount\n" +
						"5. Audit\n" +
						"0. Exit");

				System.out.print("> ");
				input = scanner.nextLine();
				switch(input) {
					case "1":
						openAccount();
						continue;
					case "2":
						sendAmount();
						continue;
					case "3":
						checkAccount();
						continue;
					case "4":
						receiveAmount();
						continue;
					case "5":
						audit();
						continue;
					case "0":
						scanner.close();
						System.out.println("Exiting client...");
                        ClientAPI.close();
						return;
					default:
						continue;
				}
			}
	    }
	}
    public void openAccount() {
        try {
            long userID = ClientAPI.open_account(pubKey, privKey);
            System.out.println("Created a new bank account with ID = " + userID);
        } catch (StatusRuntimeException | NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            System.out.println(e.getMessage());
        }
    }

    public void sendAmount() {
        long ID = getID('A');
        if(ID == -1) return;

        int amount = getAmount();
        if(amount == -1) return;

        if (!verifyUserInput("Are you sure you create a transfer of " + amount + " euros to user ID = " + ID + " ?"))
            return;

        try {
            PublicKey destKey = ClientAPI.get_public_key(ID);
            long TID = ClientAPI.send_amount(pubKey, destKey, amount, privKey);
            System.out.println("Created a pending transfer with TID = " + TID);
        } catch (StatusRuntimeException | NoSuchAlgorithmException | SignatureException | InvalidKeyException | InvalidKeySpecException e) {
            System.out.println(e.getMessage());
        }
    }

    public void checkAccount() {
        try {
            HashMap<String, Object> acc_info = ClientAPI.check_account(pubKey, privKey);
            System.out.println("\n[checkAccount] Balance: " + acc_info.get("balance").toString() + ".\nPending Transfers:");
            for (Transfer t: (Iterable<Transfer>) acc_info.get("pendingTransfers")) {
                System.out.println(t);
            }
        } catch (StatusRuntimeException | NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            System.out.println(e.getMessage());
        }
    }

    public void receiveAmount() {
        long TID = getID('T');
        if(TID == -1) return;

        if (!verifyUserInput("Are you sure you want to complete transfer ID " + TID + " ?"))
            return;

        try {
            int amount = ClientAPI.receive_amount(pubKey, TID, privKey);
            System.out.println("Received amount of " + amount + " euros.");
        } catch (StatusRuntimeException | NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            System.out.println(e.getMessage());
        }

    }

    public boolean verifyUserInput(String s) {
        String input;
        Scanner scanner = new Scanner(System.in);
        System.out.println(s + " (Y/N): ");
        System.out.print("> ");
        input = scanner.next();
        while(true){
            switch (input) {
                case "Y":
                    return true;
                case "N":
                    return false;
                default:
                    continue;

            }
        }
    }

    public void audit() {
        try {
            Iterable<Transfer> acc_trans = ClientAPI.audit(pubKey, privKey);
            System.out.println("\n[audit] Transaction history:");
            for (Transfer t: (Iterable<Transfer>) acc_trans) {
                System.out.println(t);
            }
        } catch (StatusRuntimeException | NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            System.out.println(e.getMessage());
        }

    }

    public int getAmount() {
        int amount = -1;

        String input;
        Scanner scanner = new Scanner(System.in);

        while(true) {
            System.out.println("Enter a valid amount or type 'exit' to return: ");
            System.out.print("> ");
            input = scanner.nextLine();
            switch (input) {
                case "exit":
                    return amount;
                default:
                    try {
                        amount = Integer.parseInt(input);
                        return amount;
                    } catch (NumberFormatException e) {
                        continue;
                    }

            }
        }
    }

    public long getID(char type) {
        long ID = -1;

        String input;
        Scanner scanner = new Scanner(System.in);

        while(true) {
            if(type == 'A') // Account ID
                System.out.println("Enter a valid account ID or type 'exit' to return: ");
            else if(type == 'T') // Transaction ID
                System.out.println("Enter a valid transaction ID or type 'exit' to return: ");

            System.out.print("> ");
            input = scanner.nextLine();
            switch (input) {
                case "exit":
                    return ID;
                default:
                    try {
                        ID = Long.parseLong(input);
                        return ID;
                    } catch (NumberFormatException e) {
                        continue;
                    }

            }
        }
    }
    private void loadKeys(){
		try{
            byte[] ivec = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
            IvParameterSpec iv = new IvParameterSpec(ivec);

			String salt = "abc";

            File file= new File("tmp/keys/" + username + "-priv");

			byte[] digest = Files.readAllBytes(file.toPath());

            file= new File("tmp/keys/" + username + "-pub");

			byte[] filePublic= Files.readAllBytes(file.toPath());

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			PBEKeySpec spec = new PBEKeySpec(this.keypwd.toCharArray(), salt.getBytes(),65536, 256);
            SecretKey key = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);

            byte[] decrypted =cipher.doFinal(digest);
			
			KeyFactory kf = KeyFactory.getInstance("RSA"); // or "EC" or whatever
			this.privKey = kf.generatePrivate(new PKCS8EncodedKeySpec(decrypted));
			this.pubKey= kf.generatePublic(new X509EncodedKeySpec(filePublic));

		}
		catch(Exception e){}
	}

    private void storeKeys() {
        try {
            byte[] ivec = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
            IvParameterSpec iv = new IvParameterSpec(ivec);
			String salt = "abc";

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			PBEKeySpec spec = new PBEKeySpec(this.keypwd.toCharArray(), salt.getBytes(),65536, 256);
            SecretKey key = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] cipherText = cipher.doFinal(this.privKey.getEncoded());

            File file= new File("tmp/keys/" + username + "-priv");
			try (FileOutputStream fw = new FileOutputStream(file)) {
				fw.write(cipherText);
			}


            file= new File("tmp/keys/" + username + "-pub");
			try (FileOutputStream fw = new FileOutputStream(file)) {
				fw.write(pubKey.getEncoded());
			}

        }
        catch (Exception e){
            System.out.println(e);
            e.printStackTrace();

        }
    }
}

