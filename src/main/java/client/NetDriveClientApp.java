package client;

import java.util.Scanner;

public class NetDriveClientApp {

    public static void main(String[] args) {
        int port = 7071;

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Masukkan Port Web lokal (tekan Enter untuk default 7071): ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                port = Integer.parseInt(input);
            }
        }

        WebBridgeServer bridge = new WebBridgeServer(port);
        bridge.start();
    }
}