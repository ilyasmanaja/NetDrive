package client;

import com.google.gson.Gson;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class NetDriveEngine {

    private static final int CONTROL_PORT = 8888;
    private static final int DATA_PORT = 8889;
    private static final int CHUNK_SIZE = 8192; // 8KB buffer

    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();
    private final Gson gson = new Gson();
    private final Consumer<String> uiEventCallback;

    private Socket controlSocket;
    private PrintWriter controlOut;
    private BufferedReader controlIn;
    private DatagramSocket udpSocket;

    private volatile boolean connected = false;
    private volatile String serverHost = "127.0.0.1";
    private volatile String username = "";
    private volatile String latestUsersCsv = "";
    private volatile String latestFilesJson = "[]";

    public NetDriveEngine(Consumer<String> uiEventCallback) {
        this.uiEventCallback = uiEventCallback;
    }

    public synchronized void connectAndLogin(String host, String user) throws Exception {
        if (connected) {
            disconnect();
        }

        this.serverHost = host;
        this.username = user;

        // 1. Buka UDP Socket ephemeral untuk menerima broadcast sinkronisasi berkas
        udpSocket = new DatagramSocket(0);
        int localUdpPort = udpSocket.getLocalPort();

        // 2. Buka TCP Control Socket ke server
        controlSocket = new Socket();
        controlSocket.connect(new InetSocketAddress(host, CONTROL_PORT), 4000);
        controlOut = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));

        // 3. Kirim perintah LOGIN|<username>|<udpPort>
        controlOut.println("LOGIN|" + user + "|" + localUdpPort);
        String response = controlIn.readLine();

        if (response == null || response.startsWith("LOGIN_ERR")) {
            String errMsg = (response != null && response.contains("|")) ? response.split("\\|", 2)[1] : "Gagal login ke server.";
            cleanupSockets();
            throw new IOException(errMsg);
        }

        this.connected = true;

        // 4. Jalankan Background Receiver Thread untuk TCP Control & UDP Sync Listener
        networkExecutor.submit(this::listenTcpControlLoop);
        networkExecutor.submit(this::listenUdpSyncLoop);

        emitEvent("STATUS", Map.of(
                "connected", true,
                "username", username,
                "serverHost", serverHost,
                "udpPort", localUdpPort
        ));
    }

    private void listenTcpControlLoop() {
        try {
            String line;
            while (connected && (line = controlIn.readLine()) != null) {
                if (line.startsWith("CHAT_ALL|")) {
                    String[] p = line.split("\\|", 4);
                    if (p.length == 4) {
                        emitEvent("CHAT_ALL", Map.of("time", p[1], "sender", p[2], "message", p[3]));
                    }
                } else if (line.startsWith("CHAT_PM|")) {
                    String[] p = line.split("\\|", 5);
                    if (p.length == 5) {
                        emitEvent("CHAT_PM", Map.of("time", p[1], "sender", p[2], "target", p[3], "message", p[4]));
                    }
                } else if (line.startsWith("USER_LIST|")) {
                    latestUsersCsv = line.substring("USER_LIST|".length());
                    emitEvent("USER_LIST", Map.of("users", latestUsersCsv));
                } else if (line.startsWith("FILE_LIST|")) {
                    latestFilesJson = line.substring("FILE_LIST|".length());
                    emitEvent("FILE_LIST", Map.of("filesJson", latestFilesJson));
                } else if (line.startsWith("SYS_ERR|")) {
                    emitEvent("SYS_ERR", Map.of("message", line.substring("SYS_ERR|".length())));
                }
            }
        } catch (IOException e) {
            if (connected) {
                emitEvent("SYS_ERR", Map.of("message", "Koneksi ke server terputus: " + e.getMessage()));
            }
        } finally {
            disconnect();
        }
    }

    private void listenUdpSyncLoop() {
        byte[] buffer = new byte[2048];
        while (connected && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                // Format dari server: SYNC_EVENT|<packetId>|<fileName>|<fileSize>|<uploader>|<timestamp>
                String[] parts = msg.split("\\|", 6);
                if (parts.length == 6 && "SYNC_EVENT".equals(parts[0])) {
                    String packetId = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);
                    String uploader = parts[4];
                    String timestamp = parts[5];

                    // Segera kirim balasan ACK|<packetId>|<username> ke alamat & port pengirim UDP server
                    String ackPayload = "ACK|" + packetId + "|" + username;
                    byte[] ackBytes = ackPayload.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket ackPacket = new DatagramPacket(
                            ackBytes, ackBytes.length, packet.getAddress(), packet.getPort());
                    udpSocket.send(ackPacket);

                    // Teruskan notifikasi sinkronisasi UDP ke Web UI via WebSocket
                    emitEvent("UDP_SYNC", Map.of(
                            "packetId", packetId,
                            "fileName", fileName,
                            "fileSize", fileSize,
                            "uploader", uploader,
                            "timestamp", timestamp
                    ));
                }
            } catch (IOException ignored) {
                // Socket ditutup saat disconnect
            }
        }
    }

    public void sendBroadcast(String message) {
        if (connected && controlOut != null && !message.isBlank()) {
            controlOut.println("BROADCAST|" + message.replace("\n", " "));
        }
    }

    public void sendPrivateMessage(String targetUser, String message) {
        if (connected && controlOut != null && !targetUser.isBlank() && !message.isBlank()) {
            controlOut.println("PRIVATE MSG|" + targetUser + "|" + message.replace("\n", " "));
        }
    }

    public void requestRefresh() {
        if (connected && controlOut != null) {
            controlOut.println("LIST");
            controlOut.println("FILES");
        }
    }

    // Binary File Upload via soket data terpisah (Port 8889) + chunking 8KB + shutdownOutput()
    public String uploadFile(String fileName, long fileSize, InputStream fileInputStream) throws IOException {
        if (!connected) {
            throw new IOException("Belum terhubung ke server.");
        }

        try (Socket dataSocket = new Socket()) {
            dataSocket.connect(new InetSocketAddress(serverHost, DATA_PORT), 5000);
            dataSocket.setSoTimeout(15000);

            try (
                    DataOutputStream dos = new DataOutputStream(dataSocket.getOutputStream());
                    DataInputStream dis = new DataInputStream(dataSocket.getInputStream());
                    BufferedInputStream bis = new BufferedInputStream(fileInputStream)
            ) {
                // Header metadata
                dos.writeUTF("UPLOAD");
                dos.writeUTF(username);
                dos.writeUTF(fileName);
                dos.writeLong(fileSize);
                dos.flush();

                // Stream biner chunking 8KB
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush();

                // Akhiri pengiriman byte tanpa menutup soket kontrol utama
                dataSocket.shutdownOutput();

                boolean success = dis.readBoolean();
                String serverReply = dis.readUTF();
                if (!success) {
                    throw new IOException(serverReply);
                }
                return serverReply;
            }
        }
    }

    // Binary File Download + Verifikasi Ukuran Berkas
    public File downloadFileToTemp(String fileName) throws IOException {
        if (!connected) {
            throw new IOException("Belum terhubung ke server.");
        }

        File tempDir = new File("client_downloads");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File outputFile = new File(tempDir, fileName);

        try (Socket dataSocket = new Socket()) {
            dataSocket.connect(new InetSocketAddress(serverHost, DATA_PORT), 5000);
            dataSocket.setSoTimeout(15000);

            try (
                    DataOutputStream dos = new DataOutputStream(dataSocket.getOutputStream());
                    DataInputStream dis = new DataInputStream(dataSocket.getInputStream())
            ) {
                dos.writeUTF("DOWNLOAD");
                dos.writeUTF(username);
                dos.writeUTF(fileName);
                dos.flush();
                dataSocket.shutdownOutput();

                boolean exists = dis.readBoolean();
                if (!exists) {
                    String err = dis.readUTF();
                    throw new IOException(err);
                }

                long expectedSize = dis.readLong();
                long totalBytesRead = 0;

                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while (totalBytesRead < expectedSize &&
                            (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, expectedSize - totalBytesRead))) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                    bos.flush();
                }

                // Verifikasi integritas ukuran file hasil unduhan
                if (totalBytesRead != expectedSize || outputFile.length() != expectedSize) {
                    outputFile.delete();
                    throw new IOException("Verifikasi ukuran unduhan gagal! Diharapkan " + expectedSize + " bytes, diterima " + totalBytesRead + " bytes.");
                }

                return outputFile;
            }
        }
    }

    public synchronized void disconnect() {
        boolean wasConnected = connected;
        connected = false;
        try {
            if (controlOut != null) {
                controlOut.println("EXIT");
            }
        } catch (Exception ignored) {
        }
        cleanupSockets();
        if (wasConnected) {
            emitEvent("STATUS", Map.of("connected", false, "username", ""));
        }
    }

    private void cleanupSockets() {
        try {
            if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
        } catch (IOException ignored) {
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }

    private void emitEvent(String type, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>(data);
        payload.put("type", type);
        uiEventCallback.accept(gson.toJson(payload));
    }

    public boolean isConnected() {
        return connected;
    }

    public String getUsername() {
        return username;
    }

    public String getServerHost() {
        return serverHost;
    }

    public String getLatestUsersCsv() {
        return latestUsersCsv;
    }

    public String getLatestFilesJson() {
        return latestFilesJson;
    }
}