package server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class FileLogger {

    private static final String LOG_FILE = "server_audit.log";

    public static synchronized void logTransaction(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) { // membuat file server_audit.log
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERROR] Gagal menulis ke log: " + e.getMessage());
        }
    }
}