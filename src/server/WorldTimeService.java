package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;

public class WorldTimeService {

    // Gunakan TimeAPI.io (zona Asia/Jakarta) yang jauh lebih stabil dan ramah Java
    private static final String API_URL = "https://timeapi.io/api/time/current/zone?timeZone=Asia/Jakarta";

    public static String getOfficialTimestamp() {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(API_URL).toURL(); // mastiin urlnya benar dan nyimpan ke objek url
            conn = (HttpURLConnection) url.openConnection(); // ngebikin koneksi http
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000); // set waktu untuk pemutusan koneksi agar gak error
            conn.setReadTimeout(5000); // sama aja

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // merubah byte stream yang diberikan server web timeapi.io, data(byte stream) yang dikirimkan server web pasti berupa byte atau 0 dan 1, jadi InputStreamReader merubah bytestream tadi yang awalnya berbentuk byte menjadi karakter (huruf, angka, tanba baca), BuffereadReader untuk menyimpan karakter" tadi menjadi satu blok teks
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder(); // objek untuk menyimpan teks dari perulangan while dibawah
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parsing field "dateTime":"..." dari JSON respon
                String json = response.toString();
                String key = "\"dateTime\":\"";
                int startIndex = json.indexOf(key);
                if (startIndex != -1) {
                    startIndex += key.length();
                    int endIndex = json.indexOf("\"", startIndex);
                    return json.substring(startIndex, endIndex);
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Gagal menghubungi API waktu: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        // Fallback jika offline
        return LocalDateTime.now().toString() + " (Local Fallback)";
    }
}