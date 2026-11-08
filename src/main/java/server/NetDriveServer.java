package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class NetDriveServer {

    private static final int PORT = 8888;

    public static void main(String[] args) {
        System.out.println("=== NETDRIVE SERVER MEMULAI ===");

        try (ServerSocket serverSocket = new ServerSocket()) {
            // Opsi SO_REUSEADDR aktif (Pilar 5)
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(PORT));

            System.out.println("[SERVER] Siap menerima koneksi di port " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Jalankan ClientHandler dalam Thread mandiri untuk multi-client
                new Thread(new ClientHandler(clientSocket)).start();
            }

        } catch (IOException e) {
            System.err.println("[SERVER FATAL] Terjadi kesalahan pada ServerSocket: " + e.getMessage());
        }
    }
}