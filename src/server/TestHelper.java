package server;

public class TestHelper {
    public static void main(String[] args) {
        System.out.println("Mengambil timestamp resmi dari World Time API...");
        String timestamp = WorldTimeService.getOfficialTimestamp();
        System.out.println("Timestamp: " + timestamp);

        String sampleLog = "[" + timestamp + "] INIT: Test system logging berhasil.";
        FileLogger.logTransaction(sampleLog);
        System.out.println("Pencatatan berhasil. Cek file server_audit.log di root project.");
    }
}