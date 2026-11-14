package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ControlClientHandler implements Runnable {

    private final Socket socket;
    private String currentUsername = null;

    public ControlClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientIp = socket.getInetAddress().getHostAddress();
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split("\\|", 3);
                String command = parts[0].trim().toUpperCase();

                switch (command) {
                    case "LOGIN" -> {
                        if (parts.length < 3) {
                            out.println("LOGIN_ERR|Format login tidak valid.");
                            continue;
                        }
                        String requestedUser = parts[1].trim();
                        int udpPort = Integer.parseInt(parts[2].trim());

                        if (requestedUser.isEmpty() || NetDriveServer.activeClients.containsKey(requestedUser)) {
                            out.println("LOGIN_ERR|Username '" + requestedUser + "' sudah digunakan atau kosong.");
                        } else {
                            this.currentUsername = requestedUser;
                            ClientSession session = new ClientSession(requestedUser, out, socket.getInetAddress(), udpPort);
                            NetDriveServer.activeClients.put(requestedUser, session);

                            String ts = WorldTimeService.getOfficialTimestamp();
                            System.out.printf("[%s] [LOGIN] '%s' bergabung dari %s (UDP Port: %d)%n", ts, requestedUser, clientIp, udpPort);
                            FileLogger.logTransaction(String.format("[%s] LOGIN: %s (%s)", ts, requestedUser, clientIp));

                            out.println("LOGIN_OK|" + requestedUser);
                            NetDriveServer.broadcastUserList();
                            NetDriveServer.sendFileListTo(session);
                        }
                    }

                    case "BROADCAST" -> {
                        if (currentUsername == null || parts.length < 2) continue;
                        String message = parts[1];
                        String ts = WorldTimeService.getOfficialTimestamp();

                        System.out.printf("[%s] [BROADCAST] %s: %s%n", ts, currentUsername, message);
                        NetDriveServer.broadcastToAll("CHAT_ALL|" + ts + "|" + currentUsername + "|" + message);
                    }

                    case "PRIVATE MSG" -> {
                        if (currentUsername == null || parts.length < 3) continue;
                        String targetUser = parts[1].trim();
                        String message = parts[2];
                        String ts = WorldTimeService.getOfficialTimestamp();

                        ClientSession targetSession = NetDriveServer.activeClients.get(targetUser);
                        ClientSession senderSession = NetDriveServer.activeClients.get(currentUsername);

                        if (targetSession != null) {
                            String packet = "CHAT_PM|" + ts + "|" + currentUsername + "|" + targetUser + "|" + message;
                            targetSession.sendMessage(packet);
                            if (senderSession != null && !targetUser.equals(currentUsername)) {
                                senderSession.sendMessage(packet);
                            }
                            System.out.printf("[%s] [PRIVATE MSG] %s -> %s: %s%n", ts, currentUsername, targetUser, message);
                        } else if (senderSession != null) {
                            senderSession.sendMessage("SYS_ERR|Pengguna '" + targetUser + "' tidak ditemukan atau offline.");
                        }
                    }

                    case "LIST" -> {
                        if (currentUsername != null) {
                            NetDriveServer.broadcastUserList();
                        }
                    }

                    case "FILES" -> {
                        if (currentUsername != null) {
                            ClientSession session = NetDriveServer.activeClients.get(currentUsername);
                            if (session != null) {
                                NetDriveServer.sendFileListTo(session);
                            }
                        }
                    }

                    case "EXIT" -> {
                        System.out.println("[CONTROL] Klien '" + currentUsername + "' meminta EXIT.");
                        return;
                    }

                    default -> out.println("SYS_ERR|Perintah tidak dikenali: " + command);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[CONTROL] Koneksi terputus (" + (currentUsername != null ? currentUsername : clientIp) + "): " + e.getMessage());
        } finally {
            if (currentUsername != null) {
                NetDriveServer.activeClients.remove(currentUsername);
                String ts = WorldTimeService.getOfficialTimestamp();
                System.out.printf("[%s] [LOGOUT] '%s' keluar dari sistem.%n", ts, currentUsername);
                FileLogger.logTransaction(String.format("[%s] LOGOUT: %s", ts, currentUsername));
                NetDriveServer.broadcastUserList();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}