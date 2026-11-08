package client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

        // 1. Validasi Host & DNS (Pilar 1)
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
            System.err.println("[ERROR] Host tidak ditemukan: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("[ERROR] Kegagalan I/O saat validasi host: " + e.getMessage());
            return;
        }

        // 2. Menu Pilihan Operasi
        System.out.println("\nPilih Operasi:");
        System.out.println("1. UPLOAD Berkas ke Server");
        System.out.println("2. DOWNLOAD Berkas dari Server");
        System.out.print("Pilihan (1/2): ");
        String pilihan = scanner.nextLine().trim();

        // 3. Membuka Koneksi TCP Soket dengan Timeout (Pilar 4)
        try (Socket socket = new Socket()) {
            System.out.println("\n[TCP] Menghubungkan ke server " + host + ":" + PORT + "...");
            socket.connect(new InetSocketAddress(serverAddress, PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            System.out.println("[TCP] Terhubung secara aman.");

            try (
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    DataInputStream dis = new DataInputStream(socket.getInputStream())
            ) {
                if ("1".equals(pilihan)) {
                    // Operasi UPLOAD
                    System.out.print("Masukkan path lengkap berkas yang ingin diunggah: ");
                    String filePath = scanner.nextLine().trim();
                    File uploadFile = new File(filePath);

                    if (!uploadFile.exists() || !uploadFile.isFile()) {
                        System.err.println("[ERROR] Berkas tidak ditemukan: " + filePath);
                        return;
                    }

                    // Kirim Header Perintah
                    dos.writeUTF("UPLOAD");
                    dos.writeUTF(uploadFile.getName());
                    dos.writeLong(uploadFile.length());
                    dos.flush();

                    System.out.println("[UPLOAD] Mengunggah " + uploadFile.getName() + " (" + uploadFile.length() + " bytes)...");

                    // Kirim Data Byte Berkas
                    try (FileInputStream fis = new FileInputStream(uploadFile);
                         BufferedInputStream bis = new BufferedInputStream(fis)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                        }
                        dos.flush();
                    }

                    socket.shutdownOutput();
                    System.out.println("[UPLOAD] Berkas selesai dikirim. Menunggu konfirmasi server...");

                    String response = dis.readUTF();
                    System.out.println("\n[SERVER RESPONSE] " + response);

                } else if ("2".equals(pilihan)) {
                    // Operasi DOWNLOAD
                    System.out.print("Masukkan nama berkas di server yang ingin diunduh (misal: tes.txt): ");
                    String fileName = scanner.nextLine().trim();

                    // Kirim Header Perintah
                    dos.writeUTF("DOWNLOAD");
                    dos.writeUTF(fileName);
                    dos.flush();

                    // Cek ketersediaan berkas dari server
                    boolean exists = dis.readBoolean();
                    if (!exists) {
                        String errMsg = dis.readUTF();
                        System.err.println("\n[SERVER RESPONSE] " + errMsg);
                    } else {
                        long fileSize = dis.readLong();
                        System.out.println("[DOWNLOAD] Berkas ditemukan (" + fileSize + " bytes). Mengunduh...");

                        // Berkas unduhan disimpan secara lokal dengan prefix "downloaded_"
                        File downloadedFile = new File("downloaded_" + fileName);
                        try (FileOutputStream fos = new FileOutputStream(downloadedFile);
                             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                            byte[] buffer = new byte[4096];
                            long totalBytesRead = 0;
                            int bytesRead;

                            while (totalBytesRead < fileSize && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                                bos.write(buffer, 0, bytesRead);
                                totalBytesRead += bytesRead;
                            }
                            bos.flush();
                        }

                        System.out.println("\n[DOWNLOAD SUKSES] Berkas berhasil disimpan sebagai: " + downloadedFile.getName());
                    }

                } else {
                    System.err.println("[ERROR] Pilihan tidak valid.");
                }

            } catch (SocketTimeoutException e) {
                System.err.println("[ERROR] Waktu tunggu baca data melebihi batas (Socket Timeout): " + e.getMessage());
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Gagal terhubung ke server: " + e.getMessage());
        }

        System.out.println("=== SESI SELESAI ===");
    }
}