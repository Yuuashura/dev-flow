# Dokumentasi API — Swagger UI

Seluruh endpoint backend terdokumentasi di satu halaman, di port api-gateway:

**<http://localhost:8080/swagger-ui.html>**

Pilih service lewat dropdown di kanan atas:

| No | Service | Endpoint |
|---|---|---|
| 1 | auth-service | 28 |
| 2 | workspace-service | 13 |
| 3 | project-service | 19 |
| 4 | integration-service | 9 |
| 5 | billing-service | 11 |

**80 endpoint, seluruhnya.** `notification-service` tidak ada di daftar karena memang
tidak punya satu pun endpoint — notifikasi dilayani `auth-service`.

## Mencoba endpoint yang terlindungi

1. Buka folder **auth-service**, jalankan `POST /api/v1/auth/login`, salin `accessToken`
   dari responsnya.
2. Klik tombol **Authorize** di kanan atas, tempel tokennya, klik Authorize.
3. Endpoint lain sudah bisa dijalankan lewat **Try it out**.

Endpoint publik — `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
`GET /api/v1/billing/plans`, `GET /api/v1/media/{id}` — bisa langsung dicoba tanpa token.

## Spesifikasi mentah

Kalau ingin diimpor ke Postman, Insomnia, atau alat lain:

| Service | URL |
|---|---|
| auth | <http://localhost:8080/api-docs/auth> |
| workspace | <http://localhost:8080/api-docs/workspace> |
| project | <http://localhost:8080/api-docs/project> |
| integration | <http://localhost:8080/api-docs/integration> |
| billing | <http://localhost:8080/api-docs/billing> |

## Cara kerjanya

Spesifikasinya **dihasilkan dari controller** saat service berjalan, bukan ditulis
sebagai berkas terpisah. Artinya dokumentasinya tidak bisa basi sendiri saat kode
berubah — endpoint baru langsung muncul, endpoint yang dihapus langsung hilang.

Tiap service menyajikan spesifikasinya di `/v3/api-docs`. Gateway meneruskannya ke
`/api-docs/{service}` dan menyajikan satu Swagger UI yang menggabungkan kelimanya, jadi
cukup satu port yang perlu dibuka.

Alamat server di dalam tiap spesifikasi sengaja dipaku ke gateway (`:8080`), bukan ke
port masing-masing service. Kalau dibiarkan bawaan, **Try it out** akan menembak port
service langsung dan melewati gateway — dokumentasinya jadi memperagakan jalur yang
bukan jalur sebenarnya, dan kesalahan rute di gateway tidak akan pernah terlihat dari
sini.

## Versi

Dua lini springdoc dipakai sekaligus, dan itu disengaja:

| Modul | Spring Boot | springdoc |
|---|---|---|
| api-gateway | 3.2.9 | `springdoc-openapi-starter-webflux-ui` 2.5.0 |
| lima service | 4.1.0 | `springdoc-openapi-starter-webmvc-ui` 3.1.1 |

Lini 3.x dibangun di atas Spring Boot 4 dan tidak jalan di gateway; lini 2.x dibangun di
atas Boot 3 dan tidak jalan di service. Menyeragamkan versinya berarti menaikkan versi
gateway ke Boot 4 — itu penggantian Spring Cloud Gateway secara keseluruhan, pekerjaan
yang jauh lebih besar dan tidak diperlukan hanya untuk dokumentasi.
