# NetDrive Cloud

**NetDrive Cloud** adalah sistem penyimpanan berkas terpusat (*multi-client cloud storage*) dengan fitur komunikasi tim *real-time* dan sinkronisasi notifikasi berbasis jaringan. Proyek ini dikembangkan untuk mata kuliah **Pemrograman Jaringan**.

Sistem memisahkan jalur komunikasi kontrol/chat, transfer data biner, dan penyiaran notifikasi sinkronisasi, sehingga proses unggah maupun unduh berkas berukuran besar tidak menghambat responsivitas aplikasi klien.

---

## Arsitektur & Protokol Jaringan

Aplikasi menggunakan arsitektur *client-server* dengan pembagian kanal komunikasi sebagai berikut:

| Kanal | Protokol | Port | Fungsi |
| :--- | :--- | :--- | :--- |
| **Control & Messaging** | TCP (persistent) | `8888` | Autentikasi sesi, daftar klien aktif, daftar metadata berkas, serta pesan *broadcast* dan *private message*. |
| **Binary File Transfer** | TCP (on-demand) | `8889` | Pengiriman dan pengambilan berkas biner dengan *buffered chunking* (8 KB) serta verifikasi ukuran berkas. |
| **Sync Notification** | UDP (`DatagramSocket`) | Dinamis (*ephemeral*) | Notifikasi setiap ada berkas baru diunggah, dengan mekanisme *timeout* dan konfirmasi `ACK`. |
| **Client Web Bridge** | HTTP & WebSocket | `7071` (dapat diubah) | Antarmuka web lokal berbasis Javalin yang menjembatani browser dengan *network engine* secara asinkron. |

### Protokol Kontrol (TCP Port 8888)

Komunikasi kontrol menggunakan protokol berbasis teks dengan pemisah karakter pipa (`|`):

| Perintah | Keterangan |
| :--- | :--- |
| `LOGIN\|<username>\|<udpPort>` | Mendaftarkan sesi klien beserta port UDP lokal untuk menerima notifikasi sinkronisasi. |
| `BROADCAST\|<pesan>` | Mengirim pesan ke seluruh pengguna yang sedang terhubung. |
| `PRIVATE MSG\|<targetUser>\|<pesan>` | Mengirim pesan pribadi kepada pengguna tujuan. |
| `LIST` | Meminta daftar pengguna yang sedang aktif. |
| `FILES` | Meminta daftar metadata berkas di server. |
| `EXIT` | Mengakhiri sesi dan menghapus registrasi pengguna di server. |

---

## Fitur Utama

- **Manajemen konkurensi server:** `ExecutorService` (`CachedThreadPool`) menangani banyak koneksi klien secara bersamaan, dan `ConcurrentHashMap` menjaga konsistensi data pengguna aktif serta registri berkas.
- **Transfer berkas biner dengan verifikasi integritas:** Menggunakan `DataInputStream`/`DataOutputStream` dengan ukuran *chunk* 8192 byte dan sinyal `shutdownOutput()`. Jumlah *byte* setiap berkas yang diunggah maupun diunduh diverifikasi sebelum dicatat ke sistem.
- **Pencatatan waktu resmi (REST API):** Terintegrasi dengan `timeapi.io` (zona `Asia/Jakarta`) melalui `HttpURLConnection` dan deserialisasi JSON dengan **Google Gson**. Waktu dicatat pada metadata berkas dan *audit log* server (`server_audit.log`), dengan *fallback* ke waktu lokal jika koneksi internet terputus.
- **Sinkronisasi real-time via UDP:** Server menyiarkan paket `SYNC_EVENT` ke seluruh klien aktif setelah unggahan selesai. Klien membalas dengan paket `ACK`, dan server mengirim ulang (*retransmission*) hingga 3 kali jika terjadi *timeout*.
- **Antarmuka web non-blocking:** Dibangun dengan **Javalin** (REST API + WebSocket) dan **Tailwind CSS**. Seluruh operasi soket berjalan di *background thread* terpisah sehingga browser tetap responsif selama transfer berkas.

---

## Teknologi & Dependensi

- **JDK:** Java 21 (Eclipse Temurin)
- **Build tool:** Apache Maven
- **Library:**
  - `com.google.code.gson:gson:2.11.0` — serialisasi dan deserialisasi JSON/DTO
  - `io.javalin:javalin:6.3.0` — HTTP server dan WebSocket bridge untuk antarmuka web klien
  - `org.slf4j:slf4j-simple:2.0.16` — logging runtime untuk Javalin

---

## Struktur Direktori

```text
NetDrive/
├── pom.xml
├── README.md
├── storage/                        # Penyimpanan berkas sisi server (dibuat otomatis)
├── client_downloads/               # Hasil unduhan klien (dibuat otomatis)
├── server_audit.log                # Log aktivitas login, logout, upload, dan download
└── src/
    ├── server/
    │   ├── NetDriveServer.java         # Entry point server, thread pool, dan registri terpusat
    │   ├── ControlClientHandler.java   # Penanganan perintah teks & chat (port 8888)
    │   ├── DataTransferHandler.java    # Penanganan transfer biner & verifikasi (port 8889)
    │   ├── UdpSyncBroadcaster.java     # Notifikasi sinkronisasi UDP dengan ACK & retry
    │   ├── ClientSession.java          # Representasi sesi klien aktif
    │   ├── FileMetadata.java           # Model metadata berkas
    │   ├── WorldTimeService.java       # Klien HTTP untuk waktu resmi (timeapi.io)
    │   ├── TimeResponseDTO.java        # DTO untuk respons JSON waktu
    │   └── FileLogger.java             # Pencatatan audit log yang thread-safe
    ├── client/
    │   ├── NetDriveClientApp.java      # Entry point aplikasi klien
    │   ├── NetDriveEngine.java         # Engine jaringan klien (TCP control, binary I/O, UDP listener)
    │   └── WebBridgeServer.java        # Javalin REST API & WebSocket server untuk frontend
    └── public/
        └── index.html                  # Halaman dashboard antarmuka pengguna
```

---

## Cara Kompilasi dan Menjalankan

### Prasyarat

- **JDK 21** dan **Apache Maven** terpasang (atau gunakan IntelliJ IDEA dengan dukungan Maven bawaan).
- Port `8888` dan `8889` tidak sedang digunakan aplikasi lain.

### 1. Kompilasi

Jalankan di direktori root proyek (atau gunakan **Reload All Maven Projects** di IDE):

```bash
mvn clean compile
```

### 2. Menjalankan Server

```bash
mvn exec:java -Dexec.mainClass="server.NetDriveServer"
```

Server akan mendengarkan koneksi TCP pada port `8888` (kontrol/chat) dan `8889` (transfer data), serta menampilkan log aktivitas di terminal.

### 3. Menjalankan Klien (Single maupun Multi-Client)

Jalankan untuk setiap instance klien:

```bash
mvn exec:java -Dexec.mainClass="client.NetDriveClientApp"
```

1. Masukkan nomor port web lokal saat diminta di konsol (tekan `Enter` untuk port default `7071` pada klien pertama, atau ketik `7072` untuk klien kedua).
2. Buka browser dan akses `http://localhost:7071` (dan `http://localhost:7072` untuk klien kedua).
3. Masukkan alamat IP server (gunakan `127.0.0.1` untuk pengujian lokal) dan *username* yang unik, lalu klik **Connect & Login**.

---

## Pengembang

- **Muhammad Raihan Ilyasman** ([@ilyasmanaja](https://github.com/ilyasmanaja)) 
- **Aulia Arief**
- **Herva Sulistia**
