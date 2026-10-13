# AFL3 — Mini Competition Manager (Backend)

Backend REST API untuk aplikasi Android **Mini Competition Manager**, yaitu pengelola turnamen *single-elimination*. Proyek ini merupakan tugas AFL3 Visual Programming.

- Satu backend dengan tiga modul: **Competition**, **Participant**, dan **Match**. Setiap modul ditangani oleh satu anggota tim dan memiliki operasi CRUD ke database.
- Bracket mendukung 2–16 peserta. Jumlah qualifier ganjil memakai *bye* deterministik tanpa memberikan *bye* dua ronde berturut-turut kepada qualifier yang sama.

## Stack

| Komponen | Versi |
|---|---|
| Kotlin | 2.3.21 |
| JDK | 25 (Temurin) |
| Spring Boot | 4.1.1 (Web MVC, Data JPA, Validation, Actuator) |
| Migrasi schema | Flyway |
| Database | PostgreSQL 16 (server: 16.14, satu datasource per environment) |
| Build | Gradle 9.7.1 (wrapper) |

## Struktur package (MVC)

```plain
src/main/kotlin/ac/sfj/afl3/
├── Afl3Application.kt
├── config/         # Konfigurasi Spring
├── controller/     # REST endpoint
├── service/        # Business logic dan batas transaksi
├── repository/     # Akses data
├── model/         # Entity dan model
├── dto/            # Objek request/response
└── exception/      # Global exception handler dan exception aplikasi
```

Aturan antar-layer:

- `controller` hanya memanggil `service` dan mengembalikan `dto`; controller tidak mengakses `repository` secara langsung.
- `service` menentukan batas transaksi (`@Transactional` / `@Transactional(readOnly = true)`); seluruh transaksi memakai datasource yang sama.
- Entity JPA tidak dikembalikan langsung ke client; petakan ke `dto` di service.
- Error dilempar sebagai exception (`ApiException` atau turunannya, mis. `ResourceNotFoundException`) dan diubah oleh `GlobalExceptionHandler` menjadi body standar `{"code":"<KODE>","message":"<pesan>"}` sesuai katalog error API (lihat *Kontrak API aplikasi → Error body dan katalog kode*).

Setiap modul menambahkan file pada layer yang sesuai, misalnya `controller/CompetitionController.kt`, `service/CompetitionService.kt`, `repository/CompetitionRepository.kt`, `model/Competition.kt`, dan `dto/CompetitionDto.kt`.

## Prasyarat

- JDK 25
- Docker dengan Compose v2 (Docker Desktop di Windows)

## Konfigurasi environment

Seluruh konfigurasi dibaca dari environment variable.

| File | Dipakai untuk | Di-commit |
|---|---|---|
| `.env.example` | Template `.env` lokal | Ya |
| `.env` | Development lokal (`compose.local.yml` dan run dari IDE/Gradle), salinan dari `.env.example` | Tidak |
| `.env.production` | Deploy manual `/production` dari laptop (`compose.server.yml` via `DOCKER_HOST`); tidak disalin ke node | Tidak |
| `.env.develop` | Deploy manual `/develop` dari laptop (`compose.server.yml` via `DOCKER_HOST`); tidak disalin ke node | Tidak |

Saat dijalankan dari IDE/Gradle, Spring Boot membaca `.env` melalui `spring.config.import: optional:file:.env[.properties]`. Environment variable yang di-set langsung (container, CI) selalu lebih diprioritaskan daripada isi `.env`.

| Variabel | Wajib | Keterangan |
|---|---|---|
| `DB_URL` | Ya | JDBC URL PostgreSQL, mis. `jdbc:postgresql://<host>:5432/miniapp_db` |
| `DB_USERNAME` | Ya | Username database aplikasi |
| `DB_PASSWORD` | Ya | Password database aplikasi |
| `DB_SCHEMA` | Tidak | Schema PostgreSQL milik environment, default `public`. Server: `production` / `develop` |
| `DB_POOL_SIZE` | Tidak | Ukuran pool Hikari, default `10` |
| `APP_ENV` | Tidak | Nama environment (`local`, `develop`, `production`), default `local` |
| `SERVER_PORT` | Tidak | Port HTTP aplikasi, default `8080` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Ya (compose lokal) | Database, user, dan password PostgreSQL lokal |
| `POSTGRES_PORT` | Tidak | Port PostgreSQL lokal di host, default `5432` |
| `APP_PORT` | Tidak | Port aplikasi di host saat dijalankan via compose lokal, default `8080` |
| `COMPOSE_PROJECT_NAME`, `API_HOST`, `APP_IMAGE` | Ya (server) | Nama project compose, host Traefik, dan image GHCR (lihat *Deploy ke server*) |
| `APP_MEM_LIMIT` | Ya (server) | Batas memori container, mis. `768m`. JVM memakai maksimal 75% dari nilai ini sebagai heap |

Untuk development lokal, salin template yang sudah di-commit:

```powershell
Copy-Item .env.example .env
```

Kredensial database server **tidak** disimpan di repository.

## Menjalankan secara lokal

### Opsi A — Database di container, aplikasi dari IDE/Gradle

```bash
docker compose -f compose.local.yml up -d postgres
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
```

### Opsi B — Database dan aplikasi di container

Image aplikasi di-build dari `Dockerfile` (lihat bagian *Docker image*). Tambahkan `--build` setiap kali source berubah.

```bash
docker compose -f compose.local.yml up -d --build
docker compose -f compose.local.yml ps      # kolom STATUS app harus menjadi "healthy"
```

Pada compose lokal, `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` di-set sehingga `/actuator/health` menampilkan status tiap komponen (mis. `db`).

### Pemeriksaan dan penghentian

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/system/status
docker compose -f compose.local.yml down      # tambahkan -v untuk menghapus data PostgreSQL lokal
```

### Test

`./gradlew test` menjalankan `contextLoads()` yang membutuhkan koneksi database. Pastikan PostgreSQL lokal sudah berjalan (langkah pertama Opsi A).

## Endpoint uji coba

`GET /system/status` memastikan aplikasi dan datasource PostgreSQL dapat diakses.

| Environment | URL |
|---|---|
| Lokal | `http://localhost:8080/system/status` |
| Develop | `https://api.vispro.satryo.pro/develop/system/status` |
| Production | `https://api.vispro.satryo.pro/production/system/status` |

- HTTP `200` bila `status` = `UP`.
- HTTP `503` bila database tidak dapat diakses. Field `error` hanya berisi nama kelas penyebab; detail lengkap ada di log aplikasi.

Contoh response (nilai bervariasi):

```json
{
  "status": "UP",
  "application": "afl3",
  "environment": "production",
  "timestamp": "2026-09-29T13:00:00Z",
  "database": {
    "status": "UP", "latencyMs": 4, "database": "miniapp_db", "schema": "production",
    "serverVersion": "16.14", "error": null
  }
}
```

## Kontrak API aplikasi

Bagian ini merangkum **Mini Competition Manager — Daftar API, revisi 29 September 2026** dan menjadi acuan implementasi backend serta Android. Semua endpoint aplikasi memakai prefix `/api`; endpoint operasional `/system/status` dan `/actuator/health` tidak memakai prefix tersebut.

### Endpoint

| Modul | Method | Route | Sukses | Fungsi |
|---|---|---|---|---|
| Competition | `GET` | `/api/competitions` | `200 OK` | Daftar kompetisi |
| Competition | `POST` | `/api/competitions` | `201 Created` | Membuat kompetisi berstatus `OPEN` |
| Competition | `GET` | `/api/competitions/{competitionId}` | `200 OK` | Detail kompetisi |
| Competition | `PUT` | `/api/competitions/{competitionId}` | `200 OK` | Mengubah kompetisi saat `OPEN` |
| Competition | `DELETE` | `/api/competitions/{competitionId}` | `204 No Content` | Menghapus kompetisi saat `OPEN` |
| Participant | `GET` | `/api/competitions/{competitionId}/participants` | `200 OK` | Daftar peserta |
| Participant | `POST` | `/api/competitions/{competitionId}/participants` | `201 Created` | Mendaftarkan peserta saat `OPEN` |
| Participant | `GET` | `/api/competitions/{competitionId}/participants/{participantId}` | `200 OK` | Detail peserta |
| Participant | `PUT` | `/api/competitions/{competitionId}/participants/{participantId}` | `200 OK` | Mengubah peserta saat `OPEN` |
| Participant | `DELETE` | `/api/competitions/{competitionId}/participants/{participantId}` | `204 No Content` | Menghapus peserta saat `OPEN` |
| Match | `POST` | `/api/competitions/{competitionId}/matches` | `201 Created` | Membuat seluruh bracket |
| Match | `GET` | `/api/competitions/{competitionId}/matches` | `200 OK` | Daftar match dalam urutan bracket |
| Match | `GET` | `/api/competitions/{competitionId}/matches/{matchId}` | `200 OK` | Detail match |
| Match | `POST` | `/api/competitions/{competitionId}/matches/{matchId}/result` | `200 OK` | Menyimpan hasil dan memajukan pemenang |
| Match | `POST` | `/api/competitions/{competitionId}/matches/{matchId}/undo` | `200 OK` | Membatalkan hasil match |
| Match | `DELETE` | `/api/competitions/{competitionId}/matches` | `204 No Content` | Menghapus bracket dan kembali ke `OPEN` |

### Model dan enum

- `Competition`: `id`, `name`, `participantType`, `status`, `createdAt`, dan `updatedAt`.
- `Participant`: `id`, `competitionId`, `name`, `members`, `createdAt`, dan `updatedAt`. `members` hanya dipakai untuk kompetisi `TEAM`; setiap member memiliki `name`.
- `Match`: `id`, `competitionId`, `participant1Id`, `participant2Id`, `winnerId`, `nextMatchId`, `round`, `matchNumber`, `score1`, `score2`, `status`, `createdAt`, dan `updatedAt`. ID peserta, pemenang, dan next match nullable sesuai posisi bracket.
- `ParticipantType`: `INDIVIDUAL`, `TEAM`.
- `CompetitionStatus`: `OPEN`, `IN_MATCH`, `COMPLETED`.
- `MatchStatus`: `PENDING`, `READY`, `COMPLETED`.

Request membuat atau mengubah kompetisi berisi `name` dan `participantType`. Request membuat atau mengubah peserta berisi `name` serta `members` bila tipenya `TEAM`. Request submit hasil berisi `winnerId` dan boleh menyertakan `score1` serta `score2`; score yang diberikan harus bilangan bulat non-negatif.

### Schema database yang disepakati

Schema mengikuti proposal dan terdiri dari tiga tabel berikut. Nama kolom memakai snake_case; seluruh timestamp bertipe `timestamptz NOT NULL`.

| Tabel | Kolom | Tipe dan constraint |
|---|---|---|
| `competition` | `id` | `bigint`, primary key |
|  | `name` | `varchar NOT NULL` |
|  | `participant_type` | `varchar NOT NULL`; `INDIVIDUAL` atau `TEAM` |
|  | `status` | `varchar NOT NULL`; `OPEN`, `IN_MATCH`, atau `COMPLETED` |
|  | `created_at`, `updated_at` | `timestamptz NOT NULL` |
| `participant` | `id` | `bigint`, primary key |
|  | `competition_id` | `bigint NOT NULL`, foreign key ke `competition`, `ON DELETE CASCADE` |
|  | `name` | `varchar NOT NULL`, unik pada `(competition_id, name)` |
|  | `members` | `jsonb NULL`; array anggota tim, atau `null` untuk individu |
|  | `created_at`, `updated_at` | `timestamptz NOT NULL` |
| `match` | `id` | `bigint`, primary key |
|  | `competition_id` | `bigint NOT NULL`, foreign key ke `competition` |
|  | `participant_1_id`, `participant_2_id` | `bigint NULL`, foreign key ke `participant` |
|  | `winner_id` | `bigint NULL`, foreign key ke `participant` |
|  | `next_match_id` | `bigint NULL`, foreign key ke `match`; `null` untuk final |
|  | `round` | `int NOT NULL`, dimulai dari 1 |
|  | `match_number` | `int NOT NULL`, unik pada `(competition_id, round, match_number)` |
|  | `score_1`, `score_2` | `int NULL` |
|  | `status` | `varchar NOT NULL`; `PENDING`, `READY`, atau `COMPLETED` |
|  | `created_at`, `updated_at` | `timestamptz NOT NULL` |

Tidak ada kolom `champion_id` pada `competition`. Champion diperoleh dari `winner_id` match final yang sudah `COMPLETED`.

### Aturan bisnis

1. Kompetisi baru berstatus `OPEN`. Nama tidak boleh kosong.
2. `participantType` tidak boleh diubah setelah kompetisi memiliki peserta.
3. Peserta hanya dapat dibuat, diubah, atau dihapus saat kompetisi `OPEN`; nama peserta unik dalam satu kompetisi.
4. Bracket menerima 2–16 peserta tanpa menambah slot sampai ukuran *power-of-two*. Peserta dipasangkan menurut urutan registrasi; qualifier terakhir menerima *bye* saat jumlah qualifier ganjil. Jika qualifier itu mendapat *bye* pada ronde sebelumnya, posisi *bye* dialihkan ke qualifier eligible terdekat. Match *bye* otomatis `COMPLETED` saat pesertanya sudah diketahui dan pemenangnya langsung maju. Generate membuat seluruh bracket dalam satu transaksi, lalu mengubah kompetisi menjadi `IN_MATCH`.
5. Match dengan dua peserta berstatus `READY`; match berikutnya `PENDING` sampai kedua slot terisi. Match *bye* yang pesertanya baru diketahui setelah source selesai otomatis menjadi `COMPLETED` tanpa submit manual.
6. Hasil hanya dapat dikirim ke match `READY`. `winnerId` harus sama dengan `participant1Id` atau `participant2Id` match tersebut.
7. Submit mengubah match menjadi `COMPLETED` dan mengisi slot pemenang pada match berikutnya. Penyelesaian final mengubah kompetisi menjadi `COMPLETED`; champion adalah `winner_id` pada match final.
8. Undo hanya diizinkan untuk match `COMPLETED` bila match berikutnya belum `COMPLETED`. Undo final mengembalikan kompetisi ke `IN_MATCH`.
9. Reset menghapus seluruh match dan mengembalikan kompetisi ke `OPEN`.
10. Generate, submit, undo, dan reset menyentuh beberapa record dan wajib atomik dalam satu transaksi tulis.

### Error body dan katalog kode

Seluruh error API memakai media type JSON dengan bentuk:

```json
{
  "code": "MATCH_NOT_READY",
  "message": "Match is not ready to receive a result"
}
```

| HTTP | Kode |
|---|---|
| `400` | `VALIDATION_ERROR`, `INVALID_WINNER`, `MEMBERS_NOT_ALLOWED` |
| `404` | `COMPETITION_NOT_FOUND`, `PARTICIPANT_NOT_FOUND`, `MATCH_NOT_FOUND` |
| `409` | `COMPETITION_NOT_OPEN`, `COMPETITION_NOT_IN_MATCH`, `PARTICIPANT_TYPE_LOCKED`, `DUPLICATE_PARTICIPANT_NAME`, `INVALID_PARTICIPANT_COUNT`, `MATCH_NOT_READY`, `MATCH_UNDO_NOT_ALLOWED` |

Validasi path bersifat hierarkis: participant atau match harus menjadi milik `competitionId` pada URL. Jika tidak, response menggunakan kode not-found untuk resource tersebut dan tidak membocorkan resource dari kompetisi lain.

#### Implementasi di kode

Kontrak error diimplementasikan di package `exception/`:

- `ApiError.kt` berisi `ApiErrorResponse` (body error), enum `ApiErrorCode` (seluruh kode error), dan `ApiException`.
- `ResourceNotFoundException.kt` adalah turunan `ApiException` untuk data yang tidak ditemukan.
- `GlobalExceptionHandler.kt` mengubah exception menjadi response.

| Sumber error | HTTP | Kode |
|---|---|---|
| `ApiException(status, code, message)` yang dilempar service | Sesuai `status` | Sesuai `code` |
| `ResourceNotFoundException("Competition" / "Participant" / "Match", id)` | `404` | `COMPETITION_NOT_FOUND` / `PARTICIPANT_NOT_FOUND` / `MATCH_NOT_FOUND` |
| Error standar Spring MVC berstatus 400 (gagal validasi `@Valid`, JSON tidak valid, tipe parameter salah) | `400` | `VALIDATION_ERROR` |
| Error standar Spring MVC lainnya (route tidak ada, method tidak didukung, dst.) | Status aslinya | `HTTP_ERROR` |
| `DataIntegrityViolationException` (pelanggaran constraint database) | `409` | `DATA_INTEGRITY_VIOLATION` |
| Exception lain yang tidak tertangani | `500` | `INTERNAL_SERVER_ERROR` |

`DATA_INTEGRITY_VIOLATION`, `HTTP_ERROR`, dan `INTERNAL_SERVER_ERROR` adalah kode tambahan dari handler, di luar katalog kontrak API. Untuk error validasi, `message` saat ini generik (`Request tidak valid`) dan belum menyebutkan field yang salah.

Contoh di service:

```kotlin
// Data tidak ditemukan -> 404 COMPETITION_NOT_FOUND
throw ResourceNotFoundException("Competition", competitionId)

// Pelanggaran aturan bisnis -> 409 COMPETITION_NOT_OPEN
throw ApiException(HttpStatus.CONFLICT, ApiErrorCode.COMPETITION_NOT_OPEN, "Competition is not open")
```

## Docker image

`Dockerfile` memakai dua stage:

1. **build** (`eclipse-temurin:25-jdk`): menjalankan `./gradlew bootJar`. Cache Gradle disimpan di BuildKit cache mount.
2. **runtime** (`eclipse-temurin:25-jre`): hanya berisi `app.jar`, dijalankan sebagai user non-root `spring` pada port `8080`.

Build context dibatasi oleh `.dockerignore` dengan pendekatan *whitelist*: hanya `gradlew`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts`, dan `src/` yang dikirim ke Docker. File seperti `.env`, `cred_db.md`, `.git`, dan `build/` tidak ikut terkirim. Jika `Dockerfile` kelak membutuhkan file lain, tambahkan pengecualian `!<path>` di `.dockerignore`.

`HEALTHCHECK` memanggil `/actuator/health` setiap 10 detik (masa tunggu awal 60 detik). Container dianggap sehat bila endpoint mengembalikan HTTP 200. Status ini ikut DOWN bila database tidak terjangkau.

Build dan jalankan image secara manual:

```bash
docker build -t afl3:local .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/miniapp_db \
  -e DB_USERNAME=<username> \
  -e DB_PASSWORD=<password> \
  afl3:local
```

## Deploy ke server

Node deploy menjalankan Traefik v3.7 (entrypoint `websecure`, certResolver `letsencrypt`, network eksternal `traefik`). Kedua environment berjalan di node yang sama dan dipisahkan berdasarkan path:

| Environment | URL dasar | Schema DB | File env | Project compose |
|---|---|---|---|---|
| Production | `https://api.vispro.satryo.pro/production` | `production` | `.env.production` | `afl3-production` |
| Develop (development bersama) | `https://api.vispro.satryo.pro/develop` | `develop` | `.env.develop` | `afl3-develop` |

Mekanisme routing:

1. Router Traefik `afl3-<env>` mencocokkan `Host(api.vispro.satryo.pro) && PathPrefix(/<env>)`.
2. Middleware `StripPrefix` melepas `/<env>` sebelum request diteruskan ke container, sehingga aplikasi selalu melayani path yang sama (`/system/status`, `/actuator/health`, dst.).
3. Traefik mengirim header `X-Forwarded-Prefix: /<env>`. Aplikasi memakai `server.forward-headers-strategy: framework`, sehingga URL yang dibentuk aplikasi tetap menyertakan prefix.

Kedua environment memakai satu file `compose.server.yml`. Perintah compose dijalankan **dari luar node**, yaitu dari runner GitHub atau laptop, dengan `DOCKER_HOST=ssh://afl3-node`. Docker CLI mengirim perintah ke Docker daemon di node melalui SSH. Konsekuensinya:

- Tidak ada file compose, file env, maupun kredensial GHCR yang disimpan di node.
- Image di-pull oleh node memakai kredensial GHCR milik sisi yang menjalankan compose, sehingga node tidak perlu `docker login`.
- Nilai env tetap tersimpan di konfigurasi container dan dapat dilihat dengan `docker inspect` oleh pengguna yang punya akses Docker di node. Hal ini tidak dapat dihindari selama konfigurasi dikirim melalui environment variable.

Nama project (`-p` / `COMPOSE_PROJECT_NAME`) wajib berbeda per environment. Jika sama, menjalankan salah satu environment akan menimpa container environment lainnya.

`compose.server.yml` memakai `pull_policy: always`, sehingga setiap `up -d` selalu menarik image dari registry. Tanpa pengaturan ini, tag mutable (`:production` / `:develop`) yang sudah ada di node tidak akan diperbarui.

Container tidak mem-publish port ke host; seluruh akses melalui Traefik. Traefik hanya meneruskan trafik ke container yang berstatus `healthy`.

### Role database per environment

Setiap environment memakai role PostgreSQL sendiri yang hanya berhak atas schema miliknya. Aplikasi develop tidak dapat membaca atau mengubah schema production, dan sebaliknya.

| Environment | Role | Schema (dimiliki role) | Password |
|---|---|---|---|
| Production | `afl3_production` | `production` | `DB_PASSWORD` di `.env.production` = secret `PRODUCTION_DB_PASSWORD` |
| Develop | `afl3_develop` | `develop` | `DB_PASSWORD` di `.env.develop` = secret `DEVELOP_DB_PASSWORD` |

Jalankan **sekali** dengan `psql` pada database `miniapp_db` sebagai superuser (mis. `postgres`). Superuser dibutuhkan karena `CREATE ROLE` memerlukan hak superuser/`CREATEROLE`, dan `CREATE SCHEMA ... AUTHORIZATION` di PostgreSQL 16 mensyaratkan pelaksana dapat `SET ROLE` ke role tujuan.

```sql
CREATE ROLE afl3_production LOGIN;
CREATE ROLE afl3_develop    LOGIN;

-- Password diisi lewat prompt (di-hash di sisi klien, tidak tercatat di log server).
-- afl3_production: DB_PASSWORD dari .env.production
-- afl3_develop   : DB_PASSWORD dari .env.develop
\password afl3_production
\password afl3_develop

-- Schema dibuat lebih dulu dan dimiliki role environment-nya,
-- sehingga Flyway tidak perlu membuat schema dan role tidak membutuhkan hak CREATE pada database.
CREATE SCHEMA production AUTHORIZATION afl3_production;
CREATE SCHEMA develop    AUTHORIZATION afl3_develop;

GRANT CONNECT ON DATABASE miniapp_db TO afl3_production, afl3_develop;

ALTER ROLE afl3_production SET search_path = production;
ALTER ROLE afl3_develop    SET search_path = develop;
```

Catatan:

- `pg_hba.conf` harus mengizinkan role `afl3_production` dan `afl3_develop` dari IP node deploy. Aturan yang hanya menyebut user tertentu akan menolak role baru.
- User admin (`cred_db.md`) hanya dipakai untuk administrasi, tidak dipakai oleh aplikasi.

### Persiapan node (sekali)

1. **User deploy** khusus, anggota grup `docker` (setara akses root di node). Shell user ini harus shell yang valid (mis. `/bin/bash`), bukan `nologin`, karena *forced command* dijalankan melalui shell user:
   ```bash
   sudo useradd --create-home --shell /bin/bash deploy
   sudo usermod -aG docker deploy
   sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
   ```
2. **SSH key khusus deploy.** Buat di laptop (PowerShell) dan biarkan passphrase kosong, karena key dipakai oleh GitHub Actions:
   ```powershell
   ssh-keygen -t ed25519 -f $HOME\.ssh\afl3-deploy -C "afl3-miniapp deploy"
   ```
   Pasang public key di node dengan pembatasan *forced command*, sehingga key ini hanya dapat mengakses Docker API dan tidak bisa membuka shell:
   ```bash
   # isi <PUBLIC-KEY> dengan isi file afl3-deploy.pub (satu baris "ssh-ed25519 AAAA... afl3-miniapp deploy")
   echo 'restrict,command="docker system dial-stdio" <PUBLIC-KEY>' | sudo tee /home/deploy/.ssh/authorized_keys
   sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
   sudo chmod 600 /home/deploy/.ssh/authorized_keys
   ```
3. **Hardening SSH.** Port SSH node harus dapat diakses runner GitHub, yang IP-nya berubah-ubah; jika node berada di belakang NAT, diperlukan port-forward.
   - Buat `/etc/ssh/sshd_config.d/00-hardening.conf` berisi `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, dan `PermitRootLogin no`.
   - Prefix `00-` dipakai karena sshd memakai nilai **pertama** yang ditemukan, sehingga file ini mengalahkan file seperti `50-cloud-init.conf`.
   - Sebelum reload, pastikan akun admin sudah bisa login dengan SSH key, dan biarkan satu sesi SSH tetap terbuka.
   - Validasi dengan `sudo sshd -t`, lalu jalankan `sudo systemctl reload ssh`.
4. **Host key untuk verifikasi.** Di laptop (PowerShell), jalankan `ssh-keyscan -p <port> <host> | Out-File -Encoding ascii $HOME\afl3-known-hosts.txt`. Redirect `>` di PowerShell 5.1 menghasilkan file UTF-16 yang tidak dapat dibaca OpenSSH.
   - Cocokkan fingerprint (`ssh-keygen -lf $HOME\afl3-known-hosts.txt`) dengan fingerprint di node (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`).
   - `<host>` harus sama persis dengan nilai variable `NODE_SSH_HOST` (IP atau nama DNS), karena known_hosts dicocokkan berdasarkan string host dan port.
   - Isi file yang sudah diverifikasi disimpan sebagai secret `NODE_SSH_KNOWN_HOSTS`.
5. **Koneksi dari laptop.** Tambahkan entri berikut di `~/.ssh/config` laptop (`C:\Users\<user>\.ssh\config`), lalu uji dengan `$env:DOCKER_HOST = "ssh://afl3-node"; docker version`:
   ```
   Host afl3-node
     HostName <host-atau-IP-node>
     User deploy
     Port <port>
     IdentityFile ~/.ssh/afl3-deploy
     IdentitiesOnly yes
   ```
6. Pastikan network `traefik` sudah ada di node, dan role serta schema database sudah dibuat (lihat *Role database per environment*).

## CI/CD (GitHub Actions)

| Workflow | Pemicu | Isi |
|---|---|---|
| `.github/workflows/ci.yml` | Pull Request ke `main` / `deploy` | `./gradlew build` (kompilasi + test) dengan service PostgreSQL 16 |
| `.github/workflows/deploy.yml` | Push ke `main` / `deploy`, atau manual (*Run workflow*) | Test → build dan push image → `docker compose up -d --wait` di node melalui SSH → smoke test `/system/status` melalui URL publik |

Seluruh job berjalan di runner GitHub (`ubuntu-latest`); tidak ada runner yang dipasang di node.

| Branch | Environment | Image yang di-deploy | Tag tambahan |
|---|---|---|---|
| `main` | `/develop` | `ghcr.io/satryo-1076012614901/afl3-miniapp:sha-<commit>` | `:develop` |
| `deploy` | `/production` | `ghcr.io/satryo-1076012614901/afl3-miniapp:sha-<commit>` | `:production` |

Job deploy memakai tag `:sha-<commit>`, sehingga image yang dijalankan selalu tepat hasil build commit tersebut. Tag `:develop` / `:production` dipakai untuk deploy manual dari laptop.

### Secrets dan variables

Atur di *Settings → Secrets and variables → Actions* pada repository:

| Nama | Jenis | Isi |
|---|---|---|
| `NODE_SSH_PRIVATE_KEY` | Secret | Isi file private key `afl3-deploy` (termasuk baris `BEGIN`/`END`) |
| `NODE_SSH_KNOWN_HOSTS` | Secret | Output `ssh-keyscan` yang sudah diverifikasi |
| `PRODUCTION_DB_PASSWORD` | Secret | `DB_PASSWORD` dari `.env.production` |
| `DEVELOP_DB_PASSWORD` | Secret | `DB_PASSWORD` dari `.env.develop` |
| `NODE_SSH_HOST` | Variable | Host atau IP SSH node |
| `NODE_SSH_USER` | Variable | `deploy` |
| `NODE_SSH_PORT` | Variable (opsional) | Port SSH, default `22` |
| `APP_MEM_LIMIT` | Variable (opsional) | Batas memori container, default `768m` |

Nilai lain ditetapkan di `deploy.yml`: host API, URL database, username `afl3_<environment>`, dan schema `<environment>`.

`APP_MEM_LIMIT` berlaku untuk kedua environment. Total batas production + develop (default 2 × `768m`) ditambah kebutuhan Traefik dan layanan lain tidak boleh melebihi RAM node.

Keamanan pada paket GitHub Free (repository private):

- Repository secrets dapat dipakai oleh workflow dari branch mana pun. Collaborator dengan akses tulis dapat membuat workflow di branch-nya sendiri yang membocorkan SSH key atau password database.
- *Forced command* pada `authorized_keys` membatasi key ke Docker API saja, tetapi akses Docker API tetap setara root di node.
- Karena itu, perubahan pada folder `.github/workflows/` wajib di-review dengan teliti. Jika ada anggota yang keluar dari tim, ganti SSH key dan password database.
- Dengan GitHub Pro/Team, secret dapat dipindahkan ke *Environments* (`develop`, `production`) yang dibatasi ke branch `main` / `deploy`.

### Deploy manual dari laptop

Dipakai untuk deploy pertama (sebelum workflow berjalan) atau bila workflow tidak tersedia. File env tetap berada di laptop.

1. Buat Personal Access Token (classic) dengan scope `write:packages` (*Settings → Developer settings → Personal access tokens → Tokens (classic)*). GHCR hanya menerima token classic.
2. Build dan push image:
   ```powershell
   docker login ghcr.io -u satryo-1076012614901      # password: token classic
   docker build -t ghcr.io/satryo-1076012614901/afl3-miniapp:develop .
   docker push ghcr.io/satryo-1076012614901/afl3-miniapp:develop
   ```
3. Jalankan di node melalui SSH:
   ```powershell
   $env:DOCKER_HOST = "ssh://afl3-node"
   docker compose -p afl3-develop --env-file .env.develop -f compose.server.yml up -d --wait
   Remove-Item Env:DOCKER_HOST
   curl.exe -s https://api.vispro.satryo.pro/develop/system/status
   ```
   Hasil yang benar: `status` = `UP`, `environment` = `develop`, `database.schema` = `develop`.
4. Untuk production: `docker tag ghcr.io/satryo-1076012614901/afl3-miniapp:develop ghcr.io/satryo-1076012614901/afl3-miniapp:production`, lalu `docker push ghcr.io/satryo-1076012614901/afl3-miniapp:production`. Setelah itu jalankan compose dengan `afl3-production` dan `.env.production`.
5. Setelah push pertama, buka pengaturan package (*Profile → Packages → afl3-miniapp → Package settings → Manage Actions access*). Pastikan repository `afl3-miniapp` terdaftar dengan role **Write**, agar workflow dapat memperbarui package yang dibuat secara manual.

### Rollback

1. Buka tab *Actions → Deploy*, lalu pilih run terakhir yang berhasil untuk environment tersebut.
2. Pilih *Re-run jobs* dan jalankan ulang job **Deploy**. Job ini men-deploy ulang image `:sha-<commit>` milik run tersebut tanpa build ulang.
3. Revert commit bermasalah di branch terkait melalui Pull Request, agar deploy berikutnya tidak mengembalikan versi bermasalah.

Rollback ini tidak mengubah tag `:develop` / `:production`. Deploy manual dari laptop tetap memakai image terbaru pada tag tersebut.

## Koneksi database dan transaksi

Aplikasi memakai satu datasource dan satu pool Hikari per environment. Flyway, JPA, dan `JdbcTemplate` memakai datasource yang sama.

Aturan untuk seluruh modul:

1. Batas transaksi berada di service. Gunakan `@Transactional(readOnly = true)` untuk operasi baca dan `@Transactional` untuk operasi tulis.
2. Operasi lintas tabel yang harus atomik—generate bracket, submit hasil, undo, dan reset—berjalan dalam satu transaksi tulis.
3. `spring.jpa.open-in-view` dinonaktifkan. Akses relasi *lazy* harus selesai di dalam service, bukan di controller.

## Migrasi database (Flyway)

- Lokasi migration: `src/main/resources/db/migration`, dengan format nama `V<versi>__<deskripsi>.sql`.
- Setiap environment memakai schema sendiri (`DB_SCHEMA`) dengan riwayat Flyway (`flyway_schema_history`) masing-masing. Hibernate memetakan entity ke schema yang sama melalui `hibernate.default_schema`.
- Tulis migration **tanpa** prefix schema (mis. `CREATE TABLE competition ...`, bukan `CREATE TABLE production.competition ...`), agar migration yang sama berlaku untuk semua environment.
- `spring.jpa.hibernate.ddl-auto: validate`: schema **hanya** diubah melalui migration. Aplikasi gagal start jika entity tidak sesuai dengan schema.
- Migration yang sudah di-merge ke `main` tidak boleh diubah. Perubahan schema dilakukan dengan migration baru.
- Jika tabel suatu modul mereferensikan tabel modul lain (foreign key), migration modul yang direferensikan harus di-merge lebih dulu.
- Usulan konvensi penomoran (**perlu disepakati tim**): versi berurutan (`V1`, `V2`, ...). Sebelum membuat merge request, lakukan rebase ke `main`. Jika nomor versi sudah terpakai, ganti nomor migration Anda ke nomor berikutnya, lalu reset database lokal (`docker compose -f compose.local.yml down -v`).

## Alur kerja Git dan deploy

Langkah kerja lengkap untuk tim ada di [`README.md`](README.md). Ringkasnya:

| Branch | Peran | Deploy | Tag image |
|---|---|---|---|
| `feature/<modul>` | Pengembangan modul oleh tiap anggota | — | — |
| `main` | Integrasi; menerima PR dari `feature/<modul>` (minimal 1 approval anggota lain) | `/develop` | `:develop` |
| `deploy` | Rilis; menerima PR dari `main` | `/production` | `:production` |

- Deploy hanya dilakukan dari branch `main` dan `deploy`.
- Deploy menggunakan GitHub Actions + Docker ke node standalone (Ubuntu 24.04). Image disimpan di GHCR.

## Belum tersedia

- Kode modul Participant dan Match beserta migration-nya.
