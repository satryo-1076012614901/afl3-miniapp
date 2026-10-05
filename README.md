# AFL3 — Mini Competition Manager (Backend)

Backend REST API untuk aplikasi Android **Mini Competition Manager** (turnamen *single-elimination*), tugas AFL3 Visual Programming. Satu backend terdiri atas tiga modul — **Competition**, **Participant**, dan **Match** — dan setiap modul dikerjakan oleh satu anggota tim.

Dokumen ini adalah panduan kerja tim: struktur folder, aturan penempatan kode, dan langkah dari mulai menulis kode, uji coba, hingga merge. Referensi teknis yang lebih rinci (environment variable, Docker image, deploy, koneksi database, dan Flyway) ada di [`HELP.md`](HELP.md).

---

## 1. Struktur folder

```plain
afl3-miniapp/
├── README.md                 # Panduan kerja tim (dokumen ini)
├── HELP.md                   # Referensi teknis: env, Docker, deploy, database, Flyway
├── .github/workflows/
│   ├── ci.yml                # Build + test untuk setiap Pull Request ke main/deploy
│   └── deploy.yml            # Deploy otomatis: main -> /develop, deploy -> /production
├── Dockerfile                # Build image aplikasi (multi-stage, JDK 25 -> JRE 25)
├── .dockerignore             # Whitelist file yang dikirim ke Docker saat build
├── compose.local.yml         # PostgreSQL 16 + aplikasi untuk development lokal
├── compose.server.yml        # Deploy di server (di belakang Traefik) untuk /production dan /develop
├── .env.example              # Template .env lokal (di-commit)
├── .env                      # Konfigurasi lokal, salinan .env.example (TIDAK di-commit)
├── .env.production           # Konfigurasi server production (TIDAK di-commit)
├── .env.develop              # Konfigurasi server develop    (TIDAK di-commit)
├── build.gradle.kts          # Dependency dan konfigurasi build
├── settings.gradle.kts
├── gradlew / gradlew.bat     # Gradle wrapper (jangan pakai Gradle lain)
├── gradle/wrapper/
└── src/
    ├── main/
    │   ├── kotlin/ac/sfj/afl3/
    │   │   ├── Afl3Application.kt   # Entry point aplikasi (jangan ditambah logika)
    │   │   ├── config/              # Konfigurasi Spring (WebConfig, dll.)
    │   │   ├── controller/          # REST endpoint
    │   │   ├── service/             # Business logic + batas transaksi
    │   │   ├── repository/          # Akses data (Spring Data JPA / JdbcTemplate)
    │   │   ├── domain/              # Entity JPA dan model domain
    │   │   ├── dto/                 # Objek request/response + mapper entity -> response
    │   │   └── exception/           # GlobalExceptionHandler dan exception aplikasi
    │   └── resources/
    │       ├── application.yaml     # Konfigurasi aplikasi (file bersama)
    │       └── db/migration/        # Migration Flyway: V<versi>__<deskripsi>.sql
    └── test/kotlin/ac/sfj/afl3/     # Test (struktur package mengikuti src/main)
```

File yang sudah ada sebagai contoh dan acuan:

| File | Fungsi |
|---|---|
| `controller/SystemController.kt` | Endpoint uji coba `GET /system/status` |
| `service/SystemStatusService.kt` | Contoh service untuk memeriksa koneksi database |
| `repository/DatabaseProbeRepository.kt` | Contoh repository berbasis `JdbcTemplate` |
| `domain/DatabaseProbe.kt` | Contoh model domain |
| `dto/SystemStatusResponse.kt` | Contoh DTO response |
| `exception/ApiError.kt` | Body error `ApiErrorResponse`, enum `ApiErrorCode` (katalog kode error), dan `ApiException` untuk pelanggaran aturan bisnis |
| `exception/ResourceNotFoundException.kt` | Turunan `ApiException` untuk data tidak ditemukan (HTTP 404, kode `<RESOURCE>_NOT_FOUND`) |
| `exception/GlobalExceptionHandler.kt` | Mengubah seluruh exception menjadi body error standar `{"code","message"}` |

---

## 2. Arsitektur dan aturan penempatan kode

### 2.1 Alur request

```plain
Client (Android)
      │  HTTP + JSON
      ▼
controller/   menerima request DTO, validasi (@Valid), memanggil service, mengembalikan response DTO
      │
      ▼
service/      business logic dan batas transaksi (@Transactional)
      │
      ▼
repository/   query ke database
      │
      ▼
PostgreSQL    schema sesuai environment (public / develop / production)

exception/GlobalExceptionHandler  <-- menangkap exception dari layer mana pun, mengembalikan {"code","message"}
```

### 2.2 Tanggung jawab dan aturan tiap layer

| Layer | Folder | Isi | Boleh memanggil | Tidak boleh |
|---|---|---|---|---|
| Controller | `controller/` | Mapping URL, validasi input, kode status HTTP | `service/`, `dto/` | Mengakses `repository/`, menulis business logic, mengembalikan entity |
| Service | `service/` | Business logic, batas transaksi, mapping entity <-> DTO | `repository/`, `domain/`, `dto/`, `exception/` | Mengetahui detail HTTP (`HttpServletRequest`, `ResponseEntity`) |
| Repository | `repository/` | Query database | `domain/` | Business logic, DTO |
| Domain | `domain/` | Entity JPA (`@Entity`) dan model domain | — | Bergantung pada layer lain |
| DTO | `dto/` | Request/response dan fungsi mapper `Entity.toResponse()` | `domain/` | Logika selain mapping |
| Exception | `exception/` | Exception aplikasi dan `GlobalExceptionHandler` | — | — |
| Config | `config/` | Bean konfigurasi Spring | — | Business logic |

Aturan tambahan:

1. **Transaksi ditentukan di service.** Beri `@Transactional(readOnly = true)` di level class service sebagai penanda operasi baca, lalu `@Transactional` pada method yang menulis data. Keduanya memakai datasource PostgreSQL yang sama.
2. **Operasi atomik berada di transaksi yang sama.** Perubahan yang menyentuh beberapa tabel (mis. membuat bracket dan mengubah status kompetisi) wajib diproses dalam satu method `@Transactional` agar seluruh perubahan commit atau rollback bersama.
3. **Entity tidak keluar dari service.** Controller selalu menerima dan mengembalikan DTO.
4. **Error dilempar sebagai exception.** Pakai `ResourceNotFoundException("Competition", id)` untuk data tidak ditemukan, dan `ApiException(HttpStatus.CONFLICT, ApiErrorCode.COMPETITION_NOT_OPEN, "...")` untuk pelanggaran aturan bisnis. Jangan membuat response error manual di controller.
5. **Error API mengikuti satu kontrak.** Seluruh response error memakai body `{"code":"<KODE>","message":"<pesan>"}`. Daftar kode error ada di [`HELP.md`](HELP.md#kontrak-api-aplikasi).
6. **Schema database hanya diubah melalui migration Flyway.** Hibernate berjalan dengan `ddl-auto: validate`, sehingga aplikasi gagal start jika entity tidak sesuai dengan tabel.

### 2.3 Konvensi penamaan

| Jenis | Pola | Contoh |
|---|---|---|
| Entity | `<Nama>` (tunggal) | `domain/Competition.kt` |
| Repository | `<Nama>Repository` | `repository/CompetitionRepository.kt` |
| Service | `<Nama>Service` | `service/CompetitionService.kt` |
| Controller | `<Nama>Controller` | `controller/CompetitionController.kt` |
| DTO | `<Nama>Request`, `<Nama>Response` dalam `dto/<Nama>Dto.kt` | `dto/CompetitionDto.kt` |
| Tabel | snake_case tunggal | `competition`, `participant`, `match` |
| Migration | `V<versi>__<aksi>_<objek>.sql` | `V1__create_competition.sql` |
| URL | diawali `/api`, kata benda jamak, kebab-case | `/api/competitions`, `/api/competitions/{competitionId}` |

### 2.4 Pembagian modul

| Modul | Penanggung jawab | Branch | File yang dibuat |
|---|---|---|---|
| Competition | Satryo | `feature/competition` | `domain/Competition.kt`, `domain/ParticipantType.kt`, `domain/CompetitionStatus.kt`, `repository/CompetitionRepository.kt`, `service/CompetitionService.kt`, `controller/CompetitionController.kt`, `dto/CompetitionDto.kt`, migration `V1__create_competition.sql` |
| Participant | Jessy | `feature/participant` | `domain/Participant.kt`, `repository/ParticipantRepository.kt`, `service/ParticipantService.kt`, `controller/ParticipantController.kt`, `dto/ParticipantDto.kt`, migration `participant` |
| Match | Fariz | `feature/match` | `domain/Match.kt`, `repository/MatchRepository.kt`, `service/MatchService.kt`, `controller/MatchController.kt`, `dto/MatchDto.kt`, migration `match` |

**File bersama** — `application.yaml`, `build.gradle.kts`, `config/`, `exception/GlobalExceptionHandler.kt`, `Dockerfile`, dan file `compose*.yml` — dipakai semua modul. Perubahan pada file bersama wajib didiskusikan dulu dengan tim dan diajukan sebagai Pull Request kecil tersendiri agar tidak menimbulkan konflik.

Jika tabel suatu modul memiliki foreign key ke tabel modul lain (mis. `participant` ke `competition`), migration modul yang direferensikan harus di-merge lebih dulu.

### 2.5 Contoh satu modul

Contoh berikut adalah **ilustrasi ringkas** penempatan kode, bukan kontrak API lengkap. Implementasi nyata modul Competition (termasuk `participantType`, `status`, dan aturan bisnisnya) dapat dilihat langsung di file-file yang tercantum pada tabel 2.4, beserta test-nya di `src/test/kotlin/ac/sfj/afl3/controller/CompetitionControllerTests.kt`. Field, validasi, status, serta response final wajib mengikuti [kontrak API di `HELP.md`](HELP.md#kontrak-api-aplikasi).

**`src/main/resources/db/migration/V1__create_competition.sql`**

```sql
CREATE TABLE competition (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
```

Tulis migration **tanpa** prefix schema (`competition`, bukan `production.competition`) agar migration yang sama berlaku untuk semua environment.

**`domain/Competition.kt`**

```kotlin
package ac.sfj.afl3.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "competition")
class Competition(
    @Column(nullable = false, length = 100)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
```

**`repository/CompetitionRepository.kt`**

```kotlin
package ac.sfj.afl3.repository

import ac.sfj.afl3.domain.Competition
import org.springframework.data.jpa.repository.JpaRepository

interface CompetitionRepository : JpaRepository<Competition, Long>
```

**`dto/CompetitionDto.kt`**

```kotlin
package ac.sfj.afl3.dto

import ac.sfj.afl3.domain.Competition
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CompetitionRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
)

data class CompetitionResponse(
    val id: Long,
    val name: String,
    val createdAt: Instant,
)

fun Competition.toResponse() = CompetitionResponse(
    id = requireNotNull(id),
    name = name,
    createdAt = createdAt,
)
```

**`service/CompetitionService.kt`**

```kotlin
package ac.sfj.afl3.service

import ac.sfj.afl3.domain.Competition
import ac.sfj.afl3.dto.CompetitionRequest
import ac.sfj.afl3.dto.CompetitionResponse
import ac.sfj.afl3.dto.toResponse
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.repository.CompetitionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)               // default: operasi baca
class CompetitionService(private val competitionRepository: CompetitionRepository) {

    fun findAll(): List<CompetitionResponse> =
        competitionRepository.findAll().map { it.toResponse() }

    fun findById(id: Long): CompetitionResponse =
        getEntity(id).toResponse()

    @Transactional                            // operasi tulis
    fun create(request: CompetitionRequest): CompetitionResponse =
        competitionRepository.save(Competition(name = request.name)).toResponse()

    @Transactional
    fun update(id: Long, request: CompetitionRequest): CompetitionResponse {
        val competition = getEntity(id)       // dibaca dalam transaksi tulis yang sama
        competition.name = request.name       // disimpan otomatis saat transaksi commit
        return competition.toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        competitionRepository.delete(getEntity(id))
    }

    private fun getEntity(id: Long): Competition =
        competitionRepository.findByIdOrNull(id) ?: throw ResourceNotFoundException("Competition", id)
}
```

**`controller/CompetitionController.kt`**

```kotlin
package ac.sfj.afl3.controller

import ac.sfj.afl3.dto.CompetitionRequest
import ac.sfj.afl3.dto.CompetitionResponse
import ac.sfj.afl3.service.CompetitionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/competitions")
class CompetitionController(private val competitionService: CompetitionService) {

    @GetMapping
    fun findAll(): List<CompetitionResponse> = competitionService.findAll()

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): CompetitionResponse = competitionService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CompetitionRequest): CompetitionResponse =
        competitionService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: CompetitionRequest): CompetitionResponse =
        competitionService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = competitionService.delete(id)
}
```

---

## 3. Persiapan

### 3.1 Sekali oleh setiap anggota

1. Install **JDK 25** (Temurin), **Docker Desktop**, dan **Git**. IDE yang disarankan: IntelliJ IDEA.
2. Clone repository:
   ```powershell
   git clone https://github.com/satryo-1076012614901/afl3-miniapp.git
   cd afl3-miniapp
   ```
3. Buat file `.env` dari template (file `.env` tidak di-commit):
   ```powershell
   Copy-Item .env.example .env
   ```
4. Jalankan aplikasi dan pastikan lingkungan lokal berfungsi:
   ```powershell
   docker compose -f compose.local.yml up -d --build
   docker compose -f compose.local.yml ps
   Invoke-RestMethod http://localhost:8080/system/status | ConvertTo-Json -Depth 5
   ```
   Kolom STATUS kedua container harus `healthy`, dan `status` pada response harus `UP`.

---

## 4. Langkah kerja: dari mulai coding hingga merge

Contoh perintah memakai modul Competition. Ganti `competition` dengan modul Anda.

### Langkah 1 — Siapkan branch dari `main` terbaru

```powershell
git switch main
git pull
git switch -c feature/competition
```

Jika branch `feature/competition` sudah pernah di-merge sebelumnya, hapus branch lama lalu buat ulang dari `main` terbaru:

```powershell
git switch main
git pull
git branch -D feature/competition
git switch -c feature/competition
```

### Langkah 2 — Tulis kode dengan urutan berikut

1. **Migration** di `src/main/resources/db/migration/` (lihat konvensi nomor versi di Langkah 5).
2. **Entity** di `domain/`, sesuai dengan tabel di migration.
3. **Repository** di `repository/`.
4. **DTO** di `dto/`: request, response, dan mapper `toResponse()`.
5. **Service** di `service/`: business logic dan anotasi transaksi.
6. **Controller** di `controller/`.
7. **Unit test** untuk business logic murni (mis. pembentukan bracket) di `src/test/kotlin/ac/sfj/afl3/service/`, memakai JUnit 5 tanpa Spring agar cepat dan tidak membutuhkan database.

Commit secara bertahap dengan pesan yang jelas, misalnya `competition: tambah endpoint create`. Jangan meng-commit `.env`, `.env.*`, atau file kredensial lain (sudah tercantum di `.gitignore`).

### Langkah 3 — Uji coba di lokal

1. **Build** untuk memastikan kode dapat dikompilasi:
   ```powershell
   .\gradlew.bat bootJar
   ```
2. **Jalankan** aplikasi dan database:
   ```powershell
   docker compose -f compose.local.yml up -d --build
   docker compose -f compose.local.yml ps          # app harus "healthy"
   docker compose -f compose.local.yml logs -f app # lihat log bila ada masalah
   ```
3. **Cek sistem**: `GET http://localhost:8080/system/status` harus mengembalikan `status: UP`.
4. **Cek migration** sudah diterapkan:
   ```powershell
   docker compose -f compose.local.yml exec postgres psql -U miniapp -d miniapp_db -c "\dt"
   docker compose -f compose.local.yml exec postgres psql -U miniapp -d miniapp_db -c "SELECT version, description, success FROM flyway_schema_history"
   ```
5. **Uji endpoint modul**, untuk kasus berhasil maupun gagal. Contoh dengan PowerShell:
   ```powershell
   # Create -> 201
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/competitions -ContentType 'application/json' -Body '{"name":"Turnamen A","participantType":"INDIVIDUAL"}'
   # Read -> 200
   Invoke-RestMethod http://localhost:8080/api/competitions
   Invoke-RestMethod http://localhost:8080/api/competitions/1
   # Validasi gagal -> 400 { code: "VALIDATION_ERROR", message: "..." }
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/competitions -ContentType 'application/json' -Body '{"name":"","participantType":"INDIVIDUAL"}'
   # Data tidak ada -> 404 { code: "COMPETITION_NOT_FOUND", message: "..." }
   Invoke-RestMethod http://localhost:8080/api/competitions/9999
   ```
   Postman atau HTTP Client di IntelliJ juga dapat dipakai.
6. **Jalankan test** (PostgreSQL lokal harus berjalan):
   ```powershell
   .\gradlew.bat test
   ```

### Langkah 4 — Perbarui branch dengan `main` terbaru

Sebelum membuat Pull Request, gabungkan perubahan terbaru dari `main` agar konflik diselesaikan di branch Anda, bukan di `main`:

```powershell
git fetch origin
git rebase origin/main
# selesaikan konflik bila ada, lalu: git add <file> ; git rebase --continue
```

### Langkah 5 — Periksa nomor versi migration

Nomor versi migration harus unik dan berurutan (`V1`, `V2`, ...). Setelah rebase, periksa isi `src/main/resources/db/migration/`. Jika nomor versi migration Anda sudah dipakai oleh migration dari `main`, ganti nama file Anda ke nomor berikutnya yang belum terpakai, lalu reset database lokal dan ulangi uji coba Langkah 3:

```powershell
docker compose -f compose.local.yml down -v
docker compose -f compose.local.yml up -d --build
```

### Langkah 6 — Push dan buat Pull Request ke `main`

```powershell
git push -u origin feature/competition
# jika branch sudah pernah di-push lalu di-rebase:
git push --force-with-lease
```

Buka GitHub, buat Pull Request dari `feature/competition` ke `main`, lalu salin checklist berikut ke deskripsi PR:

```markdown
## Ringkasan
<!-- Apa yang ditambahkan/diubah -->

## Endpoint
<!-- Daftar endpoint baru/berubah beserta contoh request/response -->

## Checklist
- [ ] Branch sudah di-rebase ke `main` terbaru dan tidak ada konflik
- [ ] Workflow CI pada PR ini berhasil (tanda centang hijau)
- [ ] `gradlew bootJar` berhasil
- [ ] `docker compose -f compose.local.yml up -d --build` berhasil dan container app `healthy`
- [ ] `GET /system/status` mengembalikan `UP`
- [ ] Endpoint modul diuji untuk kasus berhasil dan gagal (400/404)
- [ ] Migration baru memakai nomor versi yang belum terpakai; migration lama tidak diubah
- [ ] Kode diletakkan sesuai layer (controller/service/repository/domain/dto/exception)
- [ ] Tidak ada file rahasia (`.env*`, kredensial) yang ikut di-commit
- [ ] Tidak ada perubahan file bersama tanpa kesepakatan tim
```

### Langkah 7 — Review oleh anggota lain

- PR wajib mendapat **minimal 1 approval dari anggota lain**. Penulis PR tidak me-merge PR-nya sendiri tanpa approval.
- Reviewer memastikan workflow **CI** pada PR berhasil, lalu memeriksa kesesuaian dengan aturan layer (bagian 2), batas transaksi, migration, dan checklist. Perubahan pada `.github/workflows/` harus di-review dengan sangat teliti karena workflow deploy memegang akses SSH ke server dan password database. Bila perlu, reviewer menjalankan branch tersebut di lokal:
  ```powershell
  git fetch origin
  git switch feature/competition
  docker compose -f compose.local.yml up -d --build
  ```
- Perbaikan dari hasil review di-commit ke branch yang sama lalu di-push; PR otomatis diperbarui.

### Langkah 8 — Merge ke `main` dan verifikasi di `/develop`

1. Setelah approval diperoleh dan checklist terpenuhi, PR di-merge ke `main`, lalu branch fitur dihapus.
2. `main` di-deploy ke environment development bersama. Verifikasi:
   - `https://api.vispro.satryo.pro/develop/system/status` mengembalikan `UP`.
   - Endpoint modul berfungsi di `https://api.vispro.satryo.pro/develop/<endpoint>`.
3. Anggota lain memperbarui branch mereka dengan `main` terbaru (Langkah 4).

### Langkah 9 — Rilis ke `/production`

1. Setelah fitur di `/develop` terverifikasi, buat Pull Request dari `main` ke `deploy`.
2. Setelah mendapat approval, PR di-merge; `deploy` di-deploy ke production.
3. Verifikasi `https://api.vispro.satryo.pro/production/system/status` dan endpoint yang dirilis.

> Deploy pada Langkah 8 dan 9 berjalan otomatis melalui workflow **Deploy** (tab *Actions* di GitHub): test → build image → deploy di server → smoke test `/system/status`. Jika job **Test** atau **Build** gagal, tidak ada yang di-deploy dan versi sebelumnya tetap berjalan. Jika job **Deploy** gagal, container lama sudah diganti oleh container baru yang tidak sehat, sehingga environment tersebut perlu segera diperbaiki atau di-rollback. Detail di bagian *CI/CD* pada [`HELP.md`](HELP.md).

### Ringkasan alur

```plain
feature/<modul> ──PR (1 approval)──► main ──auto deploy──► /develop
                                      │
                                      └──PR main -> deploy (1 approval)──► deploy ──auto deploy──► /production
```

---

## 5. Masalah umum

| Gejala | Penyebab | Solusi |
|---|---|---|
| Aplikasi gagal start: `Validate failed: Detected applied migration not resolved locally` | Database lokal berisi migration dari branch lain | `docker compose -f compose.local.yml down -v`, lalu jalankan ulang |
| `Migration checksum mismatch` | File migration yang sudah diterapkan diubah | Jangan ubah migration yang sudah di-merge; buat migration baru. Di lokal, reset database dengan `down -v` |
| `Schema-validation: missing table` / `missing column` | Entity tidak sesuai dengan tabel hasil migration | Samakan entity dengan migration, atau tambahkan migration baru |
| Container app `unhealthy` | Aplikasi gagal start atau database tidak terjangkau | `docker compose -f compose.local.yml logs app` |
| `port is already allocated` | Port 5432 atau 8080 sudah dipakai program lain | Ubah `POSTGRES_PORT` / `APP_PORT` di `.env` (dan `DB_URL` bila port PostgreSQL berubah) |
| `gradlew bootJar` gagal karena versi Java | JDK 25 belum terpasang | Install JDK 25, atau cukup build lewat `docker compose ... up -d --build` |
