package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UdpSyncBroadcaster {

    private static final int TIMEOUT_MS = 1000;
    private static final int MAX_RETRIES = 3;

    public static void broadcastFileSync(FileMetadata meta, Map<String, ClientSession> activeClients) {
        if (activeClients.isEmpty()) {
            return;
        }

        String packetId = UUID.randomUUID().toString().substring(0, 8);
        String payload = String.format("SYNC_EVENT|%s|%s|%d|%s|%s",
                packetId, meta.fileName, meta.fileSize, meta.uploadedBy, meta.uploadedAt);
        byte[] sendData = payload.getBytes(StandardCharsets.UTF_8);

        // Gunakan DatagramSocket ephemeral untuk mengirim & menunggu ACK dengan timeout
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            udpSocket.setSoTimeout(TIMEOUT_MS);

            Set<String> pendingUsers = new HashSet<>(activeClients.keySet());
            int attempt = 1;

            while (!pendingUsers.isEmpty() && attempt <= MAX_RETRIES) {
                for (String username : pendingUsers) {
                    ClientSession session = activeClients.get(username);
                    if (session != null && session.getUdpPort() > 0) {
                        DatagramPacket packet = new DatagramPacket(
                                sendData, sendData.length, session.getAddress(), session.getUdpPort());
                        udpSocket.send(packet);
                        System.out.printf("[UDP SYNC] Kirim notifikasi '%s' (ID:%s) ke %s:%d (Percobaan %d/%d)%n",
                                meta.fileName, packetId, session.getAddress().getHostAddress(),
                                session.getUdpPort(), attempt, MAX_RETRIES);
                    }
                }

                // Tunggu balasan ACK dari klien selama window TIMEOUT_MS
                long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                while (!pendingUsers.isEmpty() && System.currentTimeMillis() < deadline) {
                    try {
                        byte[] ackBuf = new byte[512];
                        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                        udpSocket.receive(ackPacket);

                        String ackMsg = new String(ackPacket.getData(), 0, ackPacket.getLength(), StandardCharsets.UTF_8).trim();
                        // Format balasan klien: ACK|<packetId>|<username>
                        String[] parts = ackMsg.split("\\|");
                        if (parts.length == 3 && "ACK".equals(parts[0]) && packetId.equals(parts[1])) {
                            String ackUser = parts[2];
                            if (pendingUsers.remove(ackUser)) {
                                System.out.printf("[UDP ACK] Diterima ACK sinkronisasi (ID:%s) dari '%s'%n", packetId, ackUser);
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        break; // Waktu tunggu percobaan ini habis, ulangi untuk klien yang belum ACK
                    }
                }
                attempt++;
            }

            if (!pendingUsers.isEmpty()) {
                System.err.println("[UDP WARN] Klien berikut tidak membalas ACK setelah " + MAX_RETRIES + "x percobaan: " + pendingUsers);
            }

        } catch (Exception e) {
            System.err.println("[UDP ERROR] Gagal menyiarkan sinkronisasi UDP: " + e.getMessage());
        }
    }
}