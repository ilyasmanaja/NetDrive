package server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private static final String STORAGE_DIR = "storage";

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[SERVER] Klien terhubung dari IP: " + clientIp);

        try (
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())
        ) {
            // 1. Baca Perintah (Custom Command Protocol)
            String command = dis.readUTF();

            if ("UPLOAD".equalsIgnoreCase(command)) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                File dir = new File(STORAGE_DIR);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File destinationFile = new File(dir, fileName);
                System.out.println("[SERVER] Menerima unggahan: " + fileName + " (" + fileSize + " bytes)");

                // 2. Baca Stream Biner Berkas dengan Buffered Stream
                try (FileOutputStream fos = new FileOutputStream(destinationFile);
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

                // 3. Ambil Timestamp Resmi dan Catat ke Audit Log
                String timestamp = WorldTimeService.getOfficialTimestamp();
                String logEntry = String.format("[%s] UPLOAD SUKSES: Berkas '%s' (%d bytes) dari IP %s",
                        timestamp, fileName, fileSize, clientIp);
                FileLogger.logTransaction(logEntry);

                // 4. Balas ke Klien
                dos.writeUTF("SUCCESS: Berkas berhasil diunggah pada " + timestamp);
                dos.flush();
                System.out.println("[SERVER] " + logEntry);

            } else if ("DOWNLOAD".equalsIgnoreCase(command)) {
                // Protokol DOWNLOAD: Baca nama berkas yang diminta
                String fileName = dis.readUTF();
                File targetFile = new File(STORAGE_DIR, fileName);

                if (!targetFile.exists() || !targetFile.isFile()) {
                    // Kirim sinyal bahwa berkas TIDAK ADA
                    dos.writeBoolean(false);
                    dos.writeUTF("ERROR: Berkas '" + fileName + "' tidak ditemukan di server.");
                    dos.flush();

                    String timestamp = WorldTimeService.getOfficialTimestamp();
                    String logEntry = String.format("[%s] DOWNLOAD GAGAL: Berkas '%s' tidak ditemukan (IP: %s)",
                            timestamp, fileName, clientIp);
                    FileLogger.logTransaction(logEntry);
                    System.out.println("[SERVER] " + logEntry);
                } else {
                    // Berkas ADA: Kirim sinyal true dan ukuran berkas
                    dos.writeBoolean(true);
                    long fileSize = targetFile.length();
                    dos.writeLong(fileSize);
                    dos.flush();

                    System.out.println("[SERVER] Mengirimkan berkas: " + fileName + " (" + fileSize + " bytes) ke IP: " + clientIp);

                    // Alirkan isi biner berkas ke klien
                    try (FileInputStream fis = new FileInputStream(targetFile);
                         BufferedInputStream bis = new BufferedInputStream(fis)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                        }
                        dos.flush();
                    }

                    // Ambil Timestamp Resmi dan Catat ke Audit Log
                    String timestamp = WorldTimeService.getOfficialTimestamp();
                    String logEntry = String.format("[%s] DOWNLOAD SUKSES: Berkas '%s' (%d bytes) diambil oleh IP %s",
                            timestamp, fileName, fileSize, clientIp);
                    FileLogger.logTransaction(logEntry);
                    System.out.println("[SERVER] " + logEntry);
                }

            } else {
                dos.writeUTF("ERROR: Perintah tidak dikenali.");
                dos.flush();
            }

        } catch (IOException e) {
            System.err.println("[SERVER] Koneksi klien bermasalah: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("[SERVER] Sesi klien selesai: " + clientIp);
            } catch (IOException e) {
                System.err.println("[SERVER] Gagal menutup soket klien: " + e.getMessage());
            }
        }
    }
}