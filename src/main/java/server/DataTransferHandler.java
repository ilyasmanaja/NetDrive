package server;

import com.google.gson.Gson;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

public class DataTransferHandler implements Runnable {

    private final Socket dataSocket;
    private static final int CHUNK_SIZE = 8192; // 8KB buffer
    private static final Gson gson = new Gson();

    public DataTransferHandler(Socket dataSocket) {
        this.dataSocket = dataSocket;
    }

    @Override
    public void run() {
        String clientIp = dataSocket.getInetAddress().getHostAddress();
        try (
                DataInputStream dis = new DataInputStream(dataSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(dataSocket.getOutputStream())
        ) {
            String action = dis.readUTF();

            if ("UPLOAD".equalsIgnoreCase(action)) {
                String uploader = dis.readUTF();
                String rawFileName = dis.readUTF();
                long expectedSize = dis.readLong();

                // Sanitasi nama file agar aman dari path traversal
                String fileName = new File(rawFileName).getName();
                File storageDir = new File(NetDriveServer.STORAGE_DIR);
                if (!storageDir.exists()) {
                    storageDir.mkdirs();
                }

                File targetFile = new File(storageDir, fileName);
                long totalBytesRead = 0;

                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while (totalBytesRead < expectedSize &&
                            (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, expectedSize - totalBytesRead))) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                    bos.flush();
                }

                // Verifikasi integritas ukuran file
                if (totalBytesRead != expectedSize || targetFile.length() != expectedSize) {
                    targetFile.delete();
                    dos.writeBoolean(false);
                    dos.writeUTF("ERROR: Verifikasi ukuran gagal (Diharapkan " + expectedSize + " bytes, diterima " + totalBytesRead + " bytes).");
                    dos.flush();
                    return;
                }

                String timestamp = WorldTimeService.getOfficialTimestamp();
                FileMetadata metadata = new FileMetadata(fileName, expectedSize, uploader, timestamp);
                NetDriveServer.fileRegistry.put(fileName, metadata);

                String logMsg = String.format("[%s] UPLOAD VERIFIED: '%s' (%d bytes) oleh '%s' (%s)",
                        timestamp, fileName, expectedSize, uploader, clientIp);
                System.out.println("[DATA] " + logMsg);
                FileLogger.logTransaction(logMsg);

                dos.writeBoolean(true);
                dos.writeUTF(gson.toJson(metadata));
                dos.flush();

                // Perbarui daftar file di semua klien TCP & siarkan notifikasi via UDP (Pilar 5)
                NetDriveServer.broadcastFileList();
                NetDriveServer.executorPool.submit(() ->
                        UdpSyncBroadcaster.broadcastFileSync(metadata, NetDriveServer.activeClients)
                );

            } else if ("DOWNLOAD".equalsIgnoreCase(action)) {
                String requester = dis.readUTF();
                String rawFileName = dis.readUTF();
                String fileName = new File(rawFileName).getName();
                File targetFile = new File(NetDriveServer.STORAGE_DIR, fileName);

                if (!targetFile.exists() || !targetFile.isFile()) {
                    dos.writeBoolean(false);
                    dos.writeUTF("ERROR: Berkas '" + fileName + "' tidak ditemukan di server.");
                    dos.flush();
                    return;
                }

                long fileSize = targetFile.length();
                dos.writeBoolean(true);
                dos.writeLong(fileSize);
                dos.flush();

                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(targetFile))) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    dos.flush();
                }

                // Tutup stream output biner agar klien tahu EOF sudah tercapai
                dataSocket.shutdownOutput();

                String timestamp = WorldTimeService.getOfficialTimestamp();
                String logMsg = String.format("[%s] DOWNLOAD: '%s' (%d bytes) diunduh oleh '%s' (%s)",
                        timestamp, fileName, fileSize, requester, clientIp);
                System.out.println("[DATA] " + logMsg);
                FileLogger.logTransaction(logMsg);
            }

        } catch (IOException e) {
            System.err.println("[DATA ERROR] Gagal memproses transfer biner dari " + clientIp + ": " + e.getMessage());
        } finally {
            try {
                dataSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}