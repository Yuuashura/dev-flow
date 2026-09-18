# DevFlow — Platform Manajemen Proyek Multi-Tenant

Sistem manajemen proyek dan portal klien berbasis **microservice**. Satu workspace bisa
memuat banyak proyek, banyak anggota dengan peran berbeda, dan klien yang hanya melihat
apa yang memang dibagikan kepadanya.

**Tujuh service Spring Boot + API gateway + frontend React**, 80 endpoint, 53 test
otomatis, seluruhnya berjalan lewat satu perintah Docker Compose.

---

## Arsitektur

```
                         ┌──────────────┐
      Browser  ────────▶ │   frontend   │  React 19 + Vite + Tailwind v4
                         │   :3000      │  disajikan nginx
                         └──────┬───────┘
                                │
                         ┌──────▼───────┐
                         │ api-gateway  │  Spring Cloud Gateway (reaktif)
                         │   :8080      │  satu pintu masuk + Swagger UI
                         └──────┬───────┘
         ┌────────────┬─────────┼─────────┬────────────┬─────────────┐
         │            │         │         │            │             │
   ┌─────▼────┐ ┌─────▼────┐ ┌──▼─────┐ ┌─▼────────┐ ┌─▼─────────┐ ┌─▼──────────┐
   │   auth   │ │workspace │ │project │ │integration│ │  billing  │ │notification│
   │  :8081   │ │  :8082   │ │ :8083  │ │   :8084   │ │   :8085   │ │   :8086    │
   └─────┬────┘ └─────┬────┘ └───┬────┘ └─────┬─────┘ └─────┬─────┘ └─────┬──────┘
         └────────────┴──────────┴────────────┴─────────────┴─────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
     ┌────▼─────┐         ┌─────▼──────┐        ┌─────▼─────┐
     │ Postgres │         │  RabbitMQ  │        │   Redis   │
     │  :15432  │         │   :5672    │        │   :6379   │
     └──────────┘         └────────────┘        └───────────┘
```

| Service | Port | Tanggung jawab | Endpoint |
|---|---|---|---|
| `api-gateway` | 8080 | Satu pintu masuk, verifikasi JWT, Swagger UI | — |
| `auth-service` | 8081 | Registrasi, login, OAuth, sesi, unggah gambar, notifikasi, admin | 28 |
| `workspace-service` | 8082 | Workspace, keanggotaan, undangan, peran | 13 |
| `project-service` | 8083 | Proyek, Flow pengerjaan, komentar, pencatatan waktu | 19 |
| `integration-service` | 8084 | Integrasi GitHub, webhook ber-HMAC | 9 |
| `billing-service` | 8085 | Paket langganan, pembayaran Xendit | 11 |
| `notification-service` | 8086 | Konsumen RabbitMQ, pengiriman email | — |

`notification-service` tidak punya endpoint HTTP; ia hanya mengonsumsi pesan dari
RabbitMQ. Endpoint notifikasi dilayani `auth-service`.

---

## Menjalankan

### Prasyarat

Hanya satu: **Docker Desktop**. Java, Maven, PostgreSQL, RabbitMQ, dan Redis semuanya
berjalan di dalam container — tidak perlu dipasang di mesin.

### Langkah

Berkas `.env` sudah disertakan dan terisi, jadi tidak ada yang perlu disiapkan:

```bash
docker compose up -d --build
```

Build pertama memakan waktu beberapa menit karena tujuh service Maven dikompilasi.
Setelah itu:

| | |
|---|---|
| **Aplikasi** | <http://localhost:3000> |
| **Dokumentasi API (Swagger UI)** | <http://localhost:8080/swagger-ui.html> |
| **API gateway** | <http://localhost:8080> |
| **RabbitMQ management** | <http://localhost:15672> — `guest` / `guest` |

Cek semuanya hidup:

```bash
docker compose ps
```

Harus muncul **11 container** berstatus `running`.

### Skema database

Tidak ada langkah manual. Flyway menjalankan seluruh migrasi otomatis saat
`auth-service` pertama kali start, termasuk di database yang benar-benar kosong.

Kepemilikan skemanya dibagi dengan sengaja:

| Service | Skema |
|---|---|
| `auth` | Migrasi V1–V15, termasuk tabel yang dipakai bersama |
| `integration`, `notification`, `billing` | Migrasi masing-masing |
| `workspace`, `project` | Tidak punya migrasi — tabelnya milik `auth` |

Keenam service berjalan dengan `ddl-auto: validate`. Artinya tidak ada satu pun service
yang bisa mengubah skema diam-diam: ketidakcocokan entity menggagalkan startup, bukan
mengubah tabel milik service lain.

---

## Dokumentasi API

### Swagger UI

**<http://localhost:8080/swagger-ui.html>** — seluruh 80 endpoint, satu halaman, di port
gateway. Pilih service lewat dropdown di kanan atas.

Mencoba endpoint yang terlindungi:

1. Buka folder **auth-service**, jalankan `POST /api/v1/auth/login`, salin `accessToken`.
2. Klik **Authorize** di kanan atas, tempel tokennya.
3. Endpoint lain sudah bisa dijalankan lewat **Try it out**.

Spesifikasinya dihasilkan dari controller saat service berjalan — bukan ditulis
terpisah, jadi tidak bisa basi sendiri saat kode berubah. Rinciannya di
[`SWAGGER.md`](SWAGGER.md).

### Postman

Impor [`DevFlow.postman_collection.json`](DevFlow.postman_collection.json) — 80 request
dalam lima folder.

1. Isi variabel koleksi `email` dan `password` dengan akun yang sudah didaftarkan.
   Keduanya sengaja dikosongkan di berkas ini.
2. Jalankan `POST /api/v1/auth/login` — token tersimpan otomatis ke variabel koleksi.
3. Request lain langsung bisa dijalankan. Id workspace, proyek, dan task ikut tersimpan
   sendiri saat request yang membuatnya dijalankan, jadi tidak ada uuid yang perlu
   disalin dengan tangan.

### Spesifikasi OpenAPI

Berkas mentahnya ada di [`openapi/`](openapi/) — lima berkas per service plus satu
gabungan, bisa diimpor ke alat apa pun.

---

## Akun pertama

**Tidak ada akun yang disemai otomatis.** Daftar lewat aplikasi seperti pengguna biasa.

Untuk memberi diri sendiri akses menu admin, ubah perannya langsung di database:

```bash
docker compose exec postgres psql -U postgres -d yuu_saas \
  -c "UPDATE users SET global_role = 'SUPER_ADMIN' WHERE email = 'email-anda@contoh.com';"
```

Peran admin dibaca ulang dari database pada setiap permintaan, bukan dipercaya dari
klaim di dalam token — jadi peran yang dicabut langsung berlaku, dan token lama tidak
bisa dipakai untuk menahan hak akses yang sudah hilang.

---

## Menjalankan test

```bash
cd auth-service && ./mvnw test      # 24 test
cd project-service && ./mvnw test   # 23 test
cd workspace-service && ./mvnw test #  6 test
```

53 test. Sebagian besar unit murni tanpa infrastruktur; satu test memuat konteks Spring
penuh dan karena itu butuh PostgreSQL, Redis, dan RabbitMQ berjalan.

---

## Fitur

**Multi-tenancy dan peran.** Workspace memisahkan data. Tiga peran: `OWNER`,
`DEVELOPER`, `CLIENT`. Peran proyek diutamakan atas peran workspace, jadi kolaborator
bisa diundang ke satu proyek saja tanpa diberi akses ke seluruh workspace — ia melihat
workspace itu di daftarnya, tapi hanya proyek yang mengundangnya.

**Flow pengerjaan.** Setiap proyek punya papan bertahap: Antrian → Dikerjakan → Review →
Selesai. Urutannya ditegakkan di server, bukan di tampilan, dan setiap perpindahan tahap
wajib menyertakan alasan yang tersimpan dalam transaksi yang sama. Progres proyek
diturunkan dari Flow yang selesai — tidak bisa diketik manual.

**Portal klien.** Peran `CLIENT` tidak bisa menulis, tidak bisa melihat komentar
internal, dan hanya melihat proyek yang memang ditandai dibagikan kepadanya.

**Autentikasi.** Email dan password, plus OAuth Google dan GitHub. Refresh token dengan
daftar sesi per perangkat yang bisa dicabut satu per satu. Login dibatasi per IP dan per
kombinasi email+IP lewat Redis; kalau Redis mati, login ditolak sementara alih-alih
diloloskan.

**Unggah gambar.** Foto profil dan logo diunggah sebagai berkas, bukan diambil dari URL
luar. Berkas diperiksa berdasarkan byte penandanya — bukan ekstensi maupun
`Content-Type` — lalu didekode dan disimpan ulang, sehingga muatan berbahaya yang
menempel di dalam berkas gambar ikut hilang.

**Integrasi GitHub.** Webhook diverifikasi HMAC dengan perbandingan yang waktunya tidak
bergantung isi. Tanpa signature yang sah, payload ditolak.

**Billing.** Paket Free dan Premium lewat Xendit. Status pembayaran hanya bisa diaktifkan
callback ber-token dari Xendit — klien tidak pernah bisa menyatakan dirinya sudah
membayar — dan `external_id` yang UNIQUE mencegah satu pembayaran diproses dua kali.

**Observability.** Setiap permintaan membawa `X-Request-ID` yang diteruskan antar service
dan dicetak di setiap baris log, jadi satu kegagalan bisa ditelusuri lintas tujuh service
dengan satu id.

---

## Bentuk respons error

Sama di seluruh service, dibaca satu fungsi di frontend (`services/apiError.ts`):

```json
{
  "error": {
    "code": "BAD_REQUEST",
    "message": "Tanggal mulai tidak boleh setelah tanggal target.",
    "details": ["startDate: wajib diisi"],
    "requestId": "9ffba8ea-2dd8-4c3c-afce-891bfd260371",
    "timestamp": "2026-09-18T06:37:05Z"
  }
}
```

`details[]` memuat alasan per field, dan itu yang ditampilkan ke pengguna — bukan pesan
umum. `requestId` menghubungkan respons dengan baris log di semua service yang dilewati.

---

## Berkas pendukung

| Berkas | Isi |
|---|---|
| [`Diagram-Alir-DevFlow.drawio`](Diagram-Alir-DevFlow.drawio) | Diagram alir sistem, hitam-putih, siap cetak |
| [`SWAGGER.md`](SWAGGER.md) | Cara kerja dokumentasi API |
| [`DevFlow.postman_collection.json`](DevFlow.postman_collection.json) | Koleksi Postman, 80 request |
| [`openapi/`](openapi/) | Spesifikasi OpenAPI mentah |

---

## Masalah yang sering muncul

**Service mati tak lama setelah `docker compose up`.** Lihat lognya:
`docker compose logs <nama-service>`. Penyebab paling umum adalah `JWT_SECRET` yang
belum diisi di `.env` — tanpa itu konteks Spring gagal naik.

**Build gagal di tengah, daemon Docker terputus.** Biasanya Docker kehabisan ruang.
Bersihkan cache build — aman, tidak menyentuh database:

```bash
docker builder prune -af
```

**Jangan** menjalankan `docker compose down -v`: `-v` menghapus volume PostgreSQL beserta
seluruh isi databasenya.

**Port bentrok.** PostgreSQL dipetakan ke **15432** di host, bukan 5432, supaya tidak
bentrok dengan PostgreSQL yang mungkin sudah terpasang di mesin.

**Gateway membalas 500 setelah salah satu service dibangun ulang.** Container baru
mendapat alamat IP baru sementara gateway masih menahan yang lama di cache DNS-nya.
Restart gateway: `docker compose restart api-gateway`.

---

## Teknologi

Java 21 · Spring Boot 4.1 (service) dan 3.2 (gateway) · Spring Cloud Gateway ·
Spring Security · PostgreSQL 18 · Flyway · RabbitMQ · Redis · springdoc OpenAPI ·
React 19 · TypeScript · Vite · Tailwind CSS v4 · Docker Compose
