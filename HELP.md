# AFL3 — Mini Competition Manager (Backend)

Backend REST API untuk aplikasi Android **Mini Competition Manager**, yaitu pengelola turnamen *single-elimination*. Proyek ini merupakan tugas AFL3 Visual Programming.

- Satu backend dengan tiga modul: **Competition**, **Participant**, dan **Match**. Setiap modul ditangani oleh satu anggota tim dan memiliki operasi CRUD ke database.
- Scope wajib: jumlah peserta *power-of-two* (4/8/16). Penanganan *bye* merupakan *stretch goal*.

## Stack

| Komponen | Versi |
|---|---|
| Kotlin | 2.3.21 |
| JDK | 25 (Temurin) |
| Spring Boot | 4.1.1 (Web MVC, Data JPA, Validation, Actuator) |
| Migrasi schema | Flyway |
| Database | PostgreSQL 16 (server: 16.14, endpoint read-write dan read-only terpisah) |
| Build | Gradle 9.7.1 (wrapper) |

## Struktur package (MVC)

```plain
src/main/kotlin/ac/sfj/afl3/
├── Afl3Application.kt
├── config/         # Konfigurasi (DataSourceConfig: routing RW/RO)
├── controller/     # REST endpoint
├── service/        # Business logic dan batas transaksi
├── repository/     # Akses data
├── domain/         # Entity dan model
├── dto/            # Objek request/response
└── exception/      # Global exception handler dan exception aplikasi
```

Aturan antar-layer:

- `controller` hanya memanggil `service` dan mengembalikan `dto`; controller tidak mengakses `repository` secara langsung.
- `service` menentukan batas transaksi (`@Transactional` / `@Transactional(readOnly = true)`), sehingga service juga menentukan pool RW atau RO yang dipakai.
- Entity JPA tidak dikembalikan langsung ke client; petakan ke `dto` di service.
- Error dilempar sebagai exception dan diubah oleh `GlobalExceptionHandler` menjadi body standar `{"code":"<KODE>","message":"<pesan>"}` sesuai katalog error API. Handler awal masih memakai `ProblemDetail` dan harus disesuaikan sebelum endpoint bisnis diimplementasikan.

Setiap modul menambahkan file pada layer yang sesuai, misalnya `controller/CompetitionController.kt`, `service/CompetitionService.kt`, `repository/CompetitionRepository.kt`, `domain/Competition.kt`, dan `dto/CompetitionDto.kt`.

## Prasyarat

- JDK 25
- Docker dengan Compose v2 (Docker Desktop di Windows)

## Konfigurasi environment

Seluruh konfigurasi dibaca dari environment variable.

| File | Dipakai untuk | Di-commit |
|---|---|---|
| `.env.example` | Template `.env` lokal | Ya |
| `.env` | Development lokal (`compose.local.yml` dan run dari IDE/Gradle), salinan dari `.env.example` | Tidak |
| `.env.production` | Deploy `/production` di server (`compose.server.yml`) | Tidak |
| `.env.develop` | Deploy `/develop` di server (`compose.server.yml`) | Tidak |

Saat dijalankan dari IDE/Gradle, Spring Boot membaca `.env` melalui `spring.config.import: optional:file:.env[.properties]`. Environment variable yang di-set langsung (container, CI) selalu lebih diprioritaskan daripada isi `.env`.

| Variabel | Wajib | Keterangan |
|---|---|---|
| `DB_RW_URL` | Ya | JDBC URL endpoint read-write, mis. `jdbc:postgresql://<host>:5432/miniapp_db` |
| `DB_RW_USERNAME` | Ya | Username endpoint read-write |
| `DB_RW_PASSWORD` | Ya | Password endpoint read-write |
| `DB_RO_URL` | Tidak | JDBC URL endpoint read-only; jika tidak didefinisikan, memakai `DB_RW_URL` |
| `DB_RO_USERNAME` | Tidak | Jika tidak didefinisikan, memakai `DB_RW_USERNAME` |
| `DB_RO_PASSWORD` | Tidak | Jika tidak didefinisikan, memakai `DB_RW_PASSWORD` |
| `DB_SCHEMA` | Tidak | Schema PostgreSQL milik environment, default `public`. Server: `production` / `develop` |
| `DB_RW_POOL_SIZE` / `DB_RO_POOL_SIZE` | Tidak | Ukuran pool Hikari, default `10` |
| `APP_ENV` | Tidak | Nama environment (`local`, `develop`, `production`), default `local` |
| `SERVER_PORT` | Tidak | Port HTTP aplikasi, default `8080` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Ya (compose lokal) | Database, user, dan password PostgreSQL lokal |
| `POSTGRES_PORT` | Tidak | Port PostgreSQL lokal di host, default `5432` |
| `APP_PORT` | Tidak | Port aplikasi di host saat dijalankan via compose lokal, default `8080` |
| `COMPOSE_PROJECT_NAME`, `API_HOST`, `APP_IMAGE` | Ya (server) | Nama project compose, host Traefik, dan image GHCR (lihat *Deploy ke server*) |
| `APP_MEM_LIMIT` | Ya (server) | Batas memori container, mis. `768m`. JVM memakai maksimal 75% dari nilai ini sebagai heap |

Variabel opsional yang ditulis dengan nilai kosong (mis. `DB_RO_URL=`) dianggap bernilai string kosong, bukan tidak didefinisikan. Untuk memakai nilai default, hapus atau jadikan komentar baris tersebut.

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

`GET /system/status` memastikan aplikasi berjalan dan kedua endpoint database dapat diakses. Pengecekan read-write dijalankan dalam transaksi biasa (pool RW), sedangkan pengecekan read-only dijalankan dalam transaksi `readOnly = true` (pool RO).

| Environment | URL |
|---|---|
| Lokal | `http://localhost:8080/system/status` |
| Develop | `https://api.vispro.satryo.pro/develop/system/status` |
| Production | `https://api.vispro.satryo.pro/production/system/status` |

- HTTP `200` bila `status` = `UP` (RW dan RO dapat diakses).
- HTTP `503` bila salah satu `DOWN`. Field `error` hanya berisi nama kelas penyebab; detail lengkap ada di log aplikasi.

Contoh response (nilai bervariasi):

```json
{
  "status": "UP",
  "application": "afl3",
  "environment": "production",
  "timestamp": "2026-09-29T13:00:00Z",
  "database": {
    "readWrite": {
      "status": "UP", "latencyMs": 4, "database": "miniapp_db", "schema": "production",
      "serverVersion": "16.14", "readOnlyTransaction": false, "inRecovery": false, "error": null
    },
    "readOnly": {
      "status": "UP", "latencyMs": 5, "database": "miniapp_db", "schema": "production",
      "serverVersion": "16.14", "readOnlyTransaction": true, "inRecovery": true, "error": null
    }
  }
}
```

Cara membaca hasil pengecekan RO:

- `readOnlyTransaction: true` pada `readOnly` menunjukkan pengecekan berjalan di dalam transaksi read-only. Nilai ini **tidak** membuktikan koneksi berasal dari pool RO, karena koneksi dari pool RW pun di-set read-only saat berada di transaksi read-only.
- Bukti koneksi benar-benar menuju endpoint RO adalah `inRecovery: true` pada `readOnly` (server replika/hot standby), sementara `readWrite` bernilai `false`. Jika `readOnly` juga `false`, endpoint RO sebenarnya server primary.
- Di lokal, kedua pool mengarah ke instance PostgreSQL yang sama, sehingga `inRecovery` selalu `false`.

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

- `Competition`: `id`, `name`, `participantType`, `status`, `championId` (nullable), `createdAt`, dan `updatedAt`.
- `Participant`: `id`, `competitionId`, `name`, `members`, `createdAt`, dan `updatedAt`. `members` hanya dipakai untuk kompetisi `TEAM`; setiap member memiliki `name`.
- `Match`: `id`, `competitionId`, `participant1Id`, `participant2Id`, `winnerId`, `nextMatchId`, `round`, `matchNumber`, `score1`, `score2`, `status`, `createdAt`, dan `updatedAt`. ID peserta, pemenang, dan next match nullable sesuai posisi bracket.
- `ParticipantType`: `INDIVIDUAL`, `TEAM`.
- `CompetitionStatus`: `OPEN`, `IN_MATCH`, `COMPLETED`.
- `MatchStatus`: `PENDING`, `READY`, `COMPLETED`.

Request membuat atau mengubah kompetisi berisi `name` dan `participantType`. Request membuat atau mengubah peserta berisi `name` serta `members` bila tipenya `TEAM`. Request submit hasil berisi `winnerId` dan boleh menyertakan `score1` serta `score2`; score yang diberikan harus bilangan bulat non-negatif.

### Aturan bisnis

1. Kompetisi baru berstatus `OPEN`. Nama tidak boleh kosong.
2. `participantType` tidak boleh diubah setelah kompetisi memiliki peserta.
3. Peserta hanya dapat dibuat, diubah, atau dihapus saat kompetisi `OPEN`; nama peserta unik dalam satu kompetisi.
4. Bracket MVP hanya dapat dibuat untuk tepat 4, 8, atau 16 peserta. Generate membuat seluruh match dalam satu transaksi, lalu mengubah kompetisi menjadi `IN_MATCH`.
5. Match round pertama berstatus `READY`; match berikutnya `PENDING` sampai kedua slot terisi.
6. Hasil hanya dapat dikirim ke match `READY`. `winnerId` harus sama dengan `participant1Id` atau `participant2Id` match tersebut.
7. Submit mengubah match menjadi `COMPLETED` dan mengisi slot pemenang pada match berikutnya. Penyelesaian final mengubah kompetisi menjadi `COMPLETED` dan mengisi `championId`.
8. Undo hanya diizinkan untuk match `COMPLETED` bila match berikutnya belum `COMPLETED`. Undo final mengembalikan kompetisi ke `IN_MATCH` dan mengosongkan champion.
9. Reset menghapus seluruh match, mengosongkan champion, dan mengembalikan kompetisi ke `OPEN`.
10. Generate, submit, undo, dan reset menyentuh beberapa record dan wajib atomik dalam satu transaksi read-write.

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
  -e DB_RW_URL=jdbc:postgresql://<host>:5432/miniapp_db \
  -e DB_RW_USERNAME=<username> \
  -e DB_RW_PASSWORD=<password> \
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

Kedua environment memakai satu file `compose.server.yml`. Environment dipilih melalui file env dan nama project:

```bash
docker compose -p afl3-production --env-file .env.production -f compose.server.yml up -d
docker compose -p afl3-develop    --env-file .env.develop    -f compose.server.yml up -d
```

Nama project (`-p` dan `COMPOSE_PROJECT_NAME`) wajib berbeda per environment. Jika sama, menjalankan salah satu environment akan menimpa container environment lainnya.

`compose.server.yml` memakai `pull_policy: always`, sehingga setiap `up -d` selalu menarik image terbaru. Tanpa pengaturan ini, tag `:production` / `:develop` yang sudah ada di node tidak akan diperbarui dan versi lama tetap berjalan.

### Role database per environment

Setiap environment memakai role PostgreSQL sendiri yang hanya berhak atas schema miliknya. Aplikasi develop tidak dapat membaca atau mengubah schema production, dan sebaliknya.

| Environment | Role | Schema (dimiliki role) | Password |
|---|---|---|---|
| Production | `afl3_production` | `production` | `DB_RW_PASSWORD` di `.env.production` |
| Develop | `afl3_develop` | `develop` | `DB_RW_PASSWORD` di `.env.develop` |

Jalankan **sekali** dengan `psql` di primary (endpoint RW) `miniapp_db` sebagai superuser (mis. `postgres`). Superuser dibutuhkan karena `CREATE ROLE` memerlukan hak superuser/`CREATEROLE`, dan `CREATE SCHEMA ... AUTHORIZATION` di PostgreSQL 16 mensyaratkan pelaksana dapat `SET ROLE` ke role tujuan.

```sql
CREATE ROLE afl3_production LOGIN;
CREATE ROLE afl3_develop    LOGIN;

-- Password diisi lewat prompt (di-hash di sisi klien, tidak tercatat di log server).
-- afl3_production: DB_RW_PASSWORD dari .env.production
-- afl3_develop   : DB_RW_PASSWORD dari .env.develop
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

- Jika endpoint RO adalah replika fisik (streaming replication), role dan schema ikut tereplikasi sehingga tidak perlu dibuat ulang di replika.
- `pg_hba.conf` di primary dan replika harus mengizinkan role `afl3_production` dan `afl3_develop` dari IP node deploy. Aturan yang hanya menyebut user tertentu akan menolak role baru.
- User admin (`cred_db.md`) hanya dipakai untuk administrasi, tidak dipakai oleh aplikasi.

### Persiapan sebelum deploy pertama

- `APP_IMAGE` di `.env.production` dan `.env.develop` menunjuk ke `ghcr.io/satryo-1076012614901/afl3-miniapp` dengan tag `:production` dan `:develop`.
- Package GHCR bersifat private secara default. Login di node dengan Personal Access Token ber-scope `read:packages`: `docker login ghcr.io -u satryo-1076012614901`.
- Pastikan network `traefik` sudah ada di node.
- Buat role dan schema database (lihat *Role database per environment*).
- Sesuaikan `APP_MEM_LIMIT` di kedua file env dengan RAM node. Total batas production + develop (default 2 × `768m`) ditambah kebutuhan Traefik dan layanan lain tidak boleh melebihi RAM node.

Container tidak mem-publish port ke host; seluruh akses melalui Traefik. Traefik hanya meneruskan trafik ke container yang berstatus `healthy`.

## Routing koneksi read-write / read-only

Aplikasi memiliki dua pool koneksi (`afl3-rw` dan `afl3-ro`) di belakang `LazyConnectionDataSourceProxy` (lihat `config/DataSourceConfig.kt`). Pool dipilih berdasarkan sifat transaksi:

| Konteks pemanggilan | Pool |
|---|---|
| `@Transactional(readOnly = true)` | RO |
| `@Transactional` (default) | RW |
| Tanpa transaksi | RW |
| Method baca repository Spring Data (`findById`, `findAll`, dst.) yang dipanggil **tanpa** transaksi pembungkus | RO, karena `SimpleJpaRepository` menandai method baca dengan `@Transactional(readOnly = true)` |
| Method baca repository di dalam transaksi `@Transactional` (bukan read-only) | RW, karena mengikuti transaksi yang sedang berjalan |
| Flyway | RW (`@FlywayDataSource`) |

Aturan untuk seluruh modul:

1. **Read-after-write.** Jika endpoint RO berupa replika, datanya dapat tertinggal dari RW (*replication lag*). Pembacaan yang harus langsung melihat data yang baru ditulis wajib berada di dalam transaksi `@Transactional` yang sama dengan penulisannya, bukan di transaksi read-only terpisah atau di pemanggilan repository tanpa transaksi.
2. Service yang murni membaca ditandai `@Transactional(readOnly = true)` agar beban baca diarahkan ke RO.
3. `spring.jpa.open-in-view` dinonaktifkan. Akses relasi *lazy* harus selesai di dalam service (di dalam transaksi), bukan di controller.

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

- Workflow GitHub Actions (`.github/workflows/`).
- Migration awal serta kode modul Competition, Participant, dan Match.
- Implementasi error body `code`/`message` beserta pemetaan katalog kode error API.
