package client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Scanner;

public class NetDriveClient {

    private static final int PORT = 8888;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== NETDRIVE CLIENT (SECURE STORAGE) ===");

        // 1. Input Host Server & Validasi Host/DNS (Pilar 1)
        System.out.print("Masukkan Host/IP Server (contoh: 127.0.0.1 / localhost): ");
        String host = scanner.nextLine().trim();

        InetAddress serverAddress;
        try {
            System.out.println("[DNS] Melakukan resolusi host...");
            serverAddress = InetAddress.getByName(host);
            System.out.println("[DNS] Terdeteksi IP: " + serverAddress.getHostAddress());

            System.out.print("[NETWORK] Menguji jangkauan host (isReachable)... ");
            if (serverAddress.isReachable(CONNECT_TIMEOUT_MS)) {
                System.out.println("TERJANGKAU (OK)");
            } else {
                System.out.println("TIDAK MERESPON PING (melanjutkan via koneksi TCP direct)");
            }
        } catch (UnknownHostException e) {
            System.err.println("[ERROR] Host tidak ditemukan atau tidak valid: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("[ERROR] Kegagalan I/O saat validasi host: " + e.getMessage());
            return;
        }

        // 2. Input Berkas yang Ingin Diunggah
        System.out.print("Masukkan path lengkap berkas yang ingin diunggah: ");
        String filePath = scanner.nextLine().trim();
        File uploadFile = new File(filePath);

        if (!uploadFile.exists() || !uploadFile.isFile()) {
            System.err.println("[ERROR] Berkas tidak ditemukan atau bukan berkas valid: " + filePath);
            return;
        }

        // 3. Membuka Koneksi TCP Soket dengan Timeout (Pilar 4)
        try (Socket socket = new Socket()) {
            System.out.println("[TCP] Menghubungkan ke server " + host + ":" + PORT + "...");
            socket.connect(new InetSocketAddress(serverAddress, PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS); // Timeout baca respon server
            System.out.println("[TCP] Terhubung secara aman.");

            try (
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    FileInputStream fis = new FileInputStream(uploadFile);
                    BufferedInputStream bis = new BufferedInputStream(fis)
            ) {
                // Kirim Header Perintah Sesuai Protokol Aplikasi
                dos.writeUTF("UPLOAD");
                dos.writeUTF(uploadFile.getName());
                dos.writeLong(uploadFile.length());
                dos.flush();

                System.out.println("[UPLOAD] Mengunggah " + uploadFile.getName() + " (" + uploadFile.length() + " bytes)...");

                // Alirkan Data Berkas Lewat Soket
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush();

                // Tutup output stream untuk memberi sinyal EOF ke server tanpa memutus socket
                socket.shutdownOutput();
                System.out.println("[UPLOAD] Berkas selesai dikirim. Menunggu konfirmasi server...");

                // Baca Respon dari Server
                String response = dis.readUTF();
                System.out.println("\n[SERVER RESPONSE] " + response);

            } catch (SocketTimeoutException e) {
                System.err.println("[ERROR] Waktu tunggu baca data melebihi batas (Socket Read Timeout): " + e.getMessage());
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Gagal terhubung ke server: " + e.getMessage());
        }

        System.out.println("=== SESI SELESAI ===");
    }
}