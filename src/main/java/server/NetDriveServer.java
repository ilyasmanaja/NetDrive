package server;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetDriveServer {

    public static final int CONTROL_PORT = 8888;
    public static final int DATA_PORT = 8889;
    public static final String STORAGE_DIR = "storage";

    // Thread-Pool & Thread-Safe Shared State
    public static final ExecutorService executorPool = Executors.newCachedThreadPool();
    public static final Map<String, ClientSession> activeClients = new ConcurrentHashMap<>();
    public static final Map<String, FileMetadata> fileRegistry = new ConcurrentHashMap<>();

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   NETDRIVE CLOUD SERVER (KELOMPOK 4) MEMULAI     ");
        System.out.println("==================================================");

        loadExistingStorageFiles();

        // Jalankan listener Data Binary (Port 8889) di background pool
        executorPool.submit(NetDriveServer::startDataServer);

        // Jalankan listener Control & Chat (Port 8888) di main thread
        startControlServer();
    }

    private static void loadExistingStorageFiles() {
        File dir = new File(STORAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            String initTime = WorldTimeService.getOfficialTimestamp();
            for (File f : files) {
                if (f.isFile()) {
                    fileRegistry.put(f.getName(), new FileMetadata(f.getName(), f.length(), "System", initTime));
                }
            }
        }
        System.out.println("[STORAGE] Memuat " + fileRegistry.size() + " berkas lama dari folder '" + STORAGE_DIR + "/'");
    }

    private static void startControlServer() {
        try (ServerSocket controlServer = new ServerSocket()) {
            controlServer.setReuseAddress(true);
            controlServer.bind(new InetSocketAddress(CONTROL_PORT));
            System.out.println("[SERVER] Control & Chat TCP aktif di port " + CONTROL_PORT);

            while (true) {
                Socket clientSocket = controlServer.accept();
                executorPool.submit(new ControlClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[FATAL] Control Server berhenti: " + e.getMessage());
        }
    }

    private static void startDataServer() {
        try (ServerSocket dataServer = new ServerSocket()) {
            dataServer.setReuseAddress(true);
            dataServer.bind(new InetSocketAddress(DATA_PORT));
            System.out.println("[SERVER] Binary File Transfer TCP aktif di port " + DATA_PORT);

            while (true) {
                Socket dataSocket = dataServer.accept();
                executorPool.submit(new DataTransferHandler(dataSocket));
            }
        } catch (IOException e) {
            System.err.println("[FATAL] Data Server berhenti: " + e.getMessage());
        }
    }

    public static void broadcastToAll(String message) {
        for (ClientSession session : activeClients.values()) {
            session.sendMessage(message);
        }
    }

    public static void broadcastUserList() {
        String usersCsv = String.join(",", activeClients.keySet());
        broadcastToAll("USER_LIST|" + usersCsv);
    }

    public static void broadcastFileList() {
        String jsonFiles = gson.toJson(new ArrayList<>(fileRegistry.values()));
        broadcastToAll("FILE_LIST|" + jsonFiles);
    }

    public static void sendFileListTo(ClientSession session) {
        String jsonFiles = gson.toJson(new ArrayList<>(fileRegistry.values()));
        session.sendMessage("FILE_LIST|" + jsonFiles);
    }
}