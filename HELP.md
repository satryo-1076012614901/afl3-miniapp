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

## Prasyarat

- JDK 25
- Docker dengan Compose v2 (Docker Desktop di Windows)

## Konfigurasi environment

Seluruh konfigurasi koneksi dibaca dari environment variable. Untuk development lokal, nilainya disimpan di file `.env` di root project. File ini **tidak di-commit**.

`.env` dipakai oleh dua pihak:

1. `docker compose`: interpolasi variabel pada `compose.local.yml`.
2. Spring Boot saat dijalankan dari IDE/Gradle, melalui `spring.config.import: optional:file:.env[.properties]`. Environment variable yang di-set langsung (container, CI) selalu lebih diprioritaskan daripada isi `.env`.

| Variabel | Wajib | Keterangan |
|---|---|---|
| `DB_RW_URL` | Ya | JDBC URL endpoint read-write, mis. `jdbc:postgresql://<host>:5432/miniapp_db` |
| `DB_RW_USERNAME` | Ya | Username endpoint read-write |
| `DB_RW_PASSWORD` | Ya | Password endpoint read-write |
| `DB_RO_URL` | Tidak | JDBC URL endpoint read-only; jika tidak didefinisikan, memakai `DB_RW_URL` |
| `DB_RO_USERNAME` | Tidak | Jika tidak didefinisikan, memakai `DB_RW_USERNAME` |
| `DB_RO_PASSWORD` | Tidak | Jika tidak didefinisikan, memakai `DB_RW_PASSWORD` |
| `DB_RW_POOL_SIZE` / `DB_RO_POOL_SIZE` | Tidak | Ukuran pool Hikari, default `10` |
| `SERVER_PORT` | Tidak | Port HTTP aplikasi, default `8080` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Ya (compose) | Database, user, dan password PostgreSQL lokal |
| `POSTGRES_PORT` | Tidak | Port PostgreSQL lokal di host, default `5432` |
| `APP_PORT` | Tidak | Port aplikasi di host saat dijalankan via compose, default `8080` |

Variabel opsional yang ditulis dengan nilai kosong (mis. `DB_RO_URL=`) dianggap bernilai string kosong, bukan tidak didefinisikan. Untuk memakai nilai default, hapus atau jadikan komentar baris tersebut.

Template `.env` untuk development lokal (salin menjadi `.env` di root project):

```dotenv
POSTGRES_DB=miniapp_db
POSTGRES_USER=miniapp
POSTGRES_PASSWORD=miniapp_local
POSTGRES_PORT=5432
APP_PORT=8080

DB_RW_URL=jdbc:postgresql://localhost:5432/miniapp_db
DB_RW_USERNAME=miniapp
DB_RW_PASSWORD=miniapp_local
DB_RO_URL=jdbc:postgresql://localhost:5432/miniapp_db
DB_RO_USERNAME=miniapp
DB_RO_PASSWORD=miniapp_local
```

Kredensial database server **tidak** disimpan di repository. Nilainya hanya dipakai sebagai secret pada proses deploy.

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
docker compose -f compose.local.yml down      # tambahkan -v untuk menghapus data PostgreSQL lokal
```

### Test

`./gradlew test` menjalankan `contextLoads()` yang membutuhkan koneksi database. Pastikan PostgreSQL lokal sudah berjalan (langkah pertama Opsi A).

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

## Routing koneksi read-write / read-only

Aplikasi memiliki dua pool koneksi (`afl3-rw` dan `afl3-ro`) di belakang `LazyConnectionDataSourceProxy` (lihat `DataSourceConfig` di `Afl3Application.kt`). Pool dipilih berdasarkan sifat transaksi:

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
- `spring.jpa.hibernate.ddl-auto: validate`: schema **hanya** diubah melalui migration. Aplikasi gagal start jika entity tidak sesuai dengan schema.
- Migration yang sudah di-merge ke `main` tidak boleh diubah. Perubahan schema dilakukan dengan migration baru.
- Jika tabel suatu modul mereferensikan tabel modul lain (foreign key), migration modul yang direferensikan harus di-merge lebih dulu.
- Usulan konvensi penomoran (**perlu disepakati tim**): versi berurutan (`V1`, `V2`, ...). Sebelum membuat merge request, lakukan rebase ke `main`. Jika nomor versi sudah terpakai, ganti nomor migration Anda ke nomor berikutnya, lalu reset database lokal (`docker compose -f compose.local.yml down -v`).

## Alur kerja Git dan deploy

- Setiap anggota mengembangkan modulnya di branch masing-masing, kemudian mengajukan merge request.
- Deploy hanya dilakukan dari branch `deploy` dan `main`.
- Deploy menggunakan GitHub Actions + Docker ke node standalone (Ubuntu 24.04) yang menjalankan Traefik v3.7 pada network `traefik`. Image disimpan di GHCR.
- Aplikasi memakai `server.forward-headers-strategy: native` sehingga header `X-Forwarded-*` dari Traefik diproses dengan benar.

## Belum tersedia

- Workflow GitHub Actions (`.github/workflows/`).
- Migration awal serta kode modul Competition, Participant, dan Match.
