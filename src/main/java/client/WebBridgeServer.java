package client;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebBridgeServer {

    private final int webPort;
    private final Set<WsContext> wsSessions = ConcurrentHashMap.newKeySet();
    private final Gson gson = new Gson();
    private final NetDriveEngine engine;

    public WebBridgeServer(int webPort) {
        this.webPort = webPort;
        this.engine = new NetDriveEngine(this::broadcastToBrowsers);
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.jsonMapper(new io.javalin.json.JsonMapper() {
                @Override
                public String toJsonString(Object obj, java.lang.reflect.Type type) {
                    return gson.toJson(obj, type);
                }

                @Override
                public <T> T fromJsonString(String json, java.lang.reflect.Type targetType) {
                    return gson.fromJson(json, targetType);
                }
            });
        });

        // WebSocket untuk push real-time ke browser (chat, user list, file list, notifikasi UDP)
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                wsSessions.add(ctx);
                ctx.send(buildStateJson());
            });
            ws.onClose(wsSessions::remove);
            ws.onError(wsSessions::remove);
        });

        // REST API: Ambil state awal
        app.get("/api/state", ctx -> {
            ctx.contentType("application/json").result(buildStateJson());
        });

        // REST API: Connect & Login
        app.post("/api/connect", ctx -> {
            Map<?, ?> body = gson.fromJson(ctx.body(), Map.class);
            String host = body.get("host") != null ? body.get("host").toString().trim() : "127.0.0.1";
            String username = body.get("username") != null ? body.get("username").toString().trim() : "";

            try {
                engine.connectAndLogin(host, username);
                ctx.json(Map.of("ok", true, "message", "Terhubung sebagai " + username));
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                ctx.status(400).json(Map.of("ok", false, "message", errMsg));
            }
        });

        // REST API: Disconnect
        app.post("/api/disconnect", ctx -> {
            engine.disconnect();
            ctx.json(Map.of("ok", true));
        });

        // REST API: Kirim Chat (Broadcast atau Private Message)
        app.post("/api/chat", ctx -> {
            Map<?, ?> body = gson.fromJson(ctx.body(), Map.class);
            String mode = body.get("mode") != null ? body.get("mode").toString() : "BROADCAST";
            String target = body.get("target") != null ? body.get("target").toString() : "";
            String message = body.get("message") != null ? body.get("message").toString() : "";

            if ("PRIVATE".equalsIgnoreCase(mode)) {
                engine.sendPrivateMessage(target, message);
            } else {
                engine.sendBroadcast(message);
            }
            ctx.json(Map.of("ok", true));
        });

        // REST API: Upload File Biner ke Server NetDrive
        app.post("/api/upload", ctx -> {
            UploadedFile uploaded = ctx.uploadedFile("file");
            if (uploaded == null) {
                ctx.status(400).json(Map.of("ok", false, "message", "Tidak ada berkas yang dipilih."));
                return;
            }
            try {
                String metaJson = engine.uploadFile(uploaded.filename(), uploaded.size(), uploaded.content());
                ctx.json(Map.of("ok", true, "metadata", metaJson));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("ok", false, "message", e.getMessage()));
            }
        });

        // REST API: Download File Biner dari Server NetDrive + Verifikasi Ukuran
        app.get("/api/download/{fileName}", ctx -> {
            String fileName = ctx.pathParam("fileName");
            try {
                File verifiedFile = engine.downloadFileToTemp(fileName);
                ctx.header("Content-Disposition", "attachment; filename=\"" + verifiedFile.getName() + "\"");
                ctx.header("Content-Length", String.valueOf(verifiedFile.length()));
                ctx.result(new FileInputStream(verifiedFile));
            } catch (Exception e) {
                ctx.status(404).result("Gagal mengunduh: " + e.getMessage());
            }
        });

        app.start(webPort);
        System.out.println("==================================================");
        System.out.println("   NETDRIVE WEB GUI READY -> http://localhost:" + webPort);
        System.out.println("==================================================");
    }

    private String buildStateJson() {
        return gson.toJson(Map.of(
                "type", "STATE",
                "connected", engine.isConnected(),
                "username", engine.getUsername(),
                "serverHost", engine.getServerHost(),
                "users", engine.getLatestUsersCsv(),
                "filesJson", engine.getLatestFilesJson()
        ));
    }

    private void broadcastToBrowsers(String jsonEvent) {
        for (WsContext session : wsSessions) {
            if (session.session.isOpen()) {
                session.send(jsonEvent);
            }
        }
    }
}