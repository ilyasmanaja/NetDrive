package server;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WorldTimeService {

    private static final String API_URL = "https://timeapi.io/api/time/current/zone?timeZone=Asia/Jakarta";
    private static final Gson gson = new Gson();

    public static String getOfficialTimestamp() {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "NetDrive-Server/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder jsonResponse = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    jsonResponse.append(line);
                }
                reader.close();

                // Deserialisasi JSON ke DTO via Gson (Sesuai Kegiatan 13.2)
                TimeResponseDTO timeData = gson.fromJson(jsonResponse.toString(), TimeResponseDTO.class);

                if (timeData != null && timeData.dateTime != null) {
                    // timeData.dateTime contohnya: "2026-11-08T18:04:00.123456"
                    // Ambil 19 karakter pertama untuk format standar: "YYYY-MM-DD HH:mm:ss"
                    String rawDatetime = timeData.dateTime;
                    if (rawDatetime.length() >= 19) {
                        return rawDatetime.substring(0, 19).replace("T", " ");
                    }
                    return rawDatetime;
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[FALLBACK] Gagal mengambil waktu dari TimeAPI (" + e.getMessage() + "). Memakai jam lokal.");
        }

        // Mekanisme fallback jika jaringan offline/gagal
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}