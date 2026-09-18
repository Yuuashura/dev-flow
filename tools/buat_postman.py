"""
Membangun koleksi Postman dari spesifikasi OpenAPI gabungan.

Metode, jalur, parameter, dan bentuk body diambil dari openapi/devflow-lengkap.openapi.json
— yang itu sendiri dihasilkan springdoc dari controller. Jadi koleksinya tidak bisa
melenceng dari kode: endpoint baru ikut muncul, endpoint yang dihapus ikut hilang.

Yang TIDAK bisa disimpulkan dari OpenAPI, dan karena itu ditulis tangan di bawah:
autentikasi Bearer di tingkat koleksi, dan skrip yang menyimpan id dari satu respons
untuk dipakai request berikutnya. Tanpa itu koleksinya cuma daftar URL — pemakainya
harus menyalin uuid dengan tangan di tiap langkah.

Jalankan:  python tools/buat_postman.py
"""

import io
import json
import os
import re
import uuid

SPEK = "openapi/devflow-lengkap.openapi.json"
KELUARAN = "DevFlow.postman_collection.json"

# Endpoint publik: token tidak dikirim, supaya tidak menutupi kesalahan lain.
TANPA_TOKEN = {
    "POST /api/v1/auth/register",
    "POST /api/v1/auth/login",
    "POST /api/v1/auth/refresh",
    "POST /api/v1/auth/resend-verification",
    "GET /api/v1/auth/verify-email",
    "GET /api/v1/billing/plans",
    "GET /api/v1/billing/plans/{code}",
    "GET /api/v1/media/{id}",
    "POST /api/v1/billing/webhook",
    "POST /api/xendit/webhook",
    "POST /api/v1/integrations/github/webhook",
}

# Inilah yang membuat koleksinya bisa dijalankan berurutan tanpa menyalin id manual.
SIMPAN = {
    "POST /api/v1/auth/login": [
        ("accessToken", "accessToken"), ("refreshToken", "refreshToken"),
        ("user.id", "userId"),
    ],
    "POST /api/v1/auth/register": [("accessToken", "accessToken"), ("user.id", "userId")],
    "POST /api/v1/workspaces": [("id", "workspaceId")],
    "GET /api/v1/workspaces": [("[0].id", "workspaceId")],
    "POST /api/v1/projects": [("id", "projectId")],
    "GET /api/v1/projects": [("[0].id", "projectId")],
    "POST /api/v1/projects/{id}/tasks": [("id", "taskId")],
    "GET /api/v1/projects/{id}/tasks": [("[0].id", "taskId")],
    "POST /api/v1/workspaces/{id}/invitations": [("token", "invitationToken")],
    "POST /api/v1/media": [("id", "mediaId")],
    "POST /api/v1/billing/owner/checkout": [("externalId", "externalId")],
}

# Nilai contoh untuk path variable, supaya request bisa langsung dijalankan.
PATH_VAR = {
    "id": "{{projectId}}", "projectId": "{{projectId}}", "workspaceId": "{{workspaceId}}",
    "taskId": "{{taskId}}", "entryId": "{{entryId}}", "userId": "{{userId}}",
    "targetUserId": "{{userId}}", "sessionId": "{{sessionId}}", "token": "{{invitationToken}}",
    "revisionId": "{{revisionId}}", "externalId": "{{externalId}}", "code": "FREE",
}


def skrip_simpan(kunci):
    """Skrip Postman yang menyimpan nilai dari respons ke variabel koleksi.

    Tiap penyimpanan memakai nama variabel JS sendiri. Versi pertama memakai `const v`
    berulang di satu blok — itu SyntaxError, dan akibatnya seluruh skrip mati diam-diam
    sehingga tidak ada satu pun id yang tersimpan.
    """
    baris = [
        "// Menyimpan id dari respons ini supaya request berikutnya tidak perlu",
        "// disalin manual. Dijalankan hanya kalau responsnya sukses.",
        "if (pm.response.code >= 200 && pm.response.code < 300) {",
        "    const d = pm.response.json();",
    ]
    for i, (jalur, nama) in enumerate(kunci):
        if jalur.startswith("["):
            sisa = jalur.split("]", 1)[1]
            ambil = "Array.isArray(d) && d.length ? d[0]" +                     "".join(f"?.{b}" for b in sisa.lstrip(".").split(".") if b) + " : undefined"
        else:
            ambil = "d" + "".join(f"?.{b}" for b in jalur.split("."))
        baris += [
            f"    const v{i} = {ambil};",
            f"    if (v{i}) pm.collectionVariables.set('{nama}', v{i});",
        ]
    baris.append("}")
    return baris


def contoh_body(op, komponen):
    """Body contoh dari schema di OpenAPI, direntang satu tingkat."""
    rb = op.get("requestBody", {}).get("content", {})
    media = rb.get("application/json")
    if not media:
        return None
    return isi_dari_schema(media.get("schema", {}), komponen)


def isi_dari_schema(sch, komponen, dalam=0):
    if dalam > 3 or not isinstance(sch, dict):
        return {}
    if "$ref" in sch:
        nama = sch["$ref"].split("/")[-1]
        return isi_dari_schema(komponen.get(nama, {}), komponen, dalam + 1)
    t = sch.get("type")
    if t == "array":
        return [isi_dari_schema(sch.get("items", {}), komponen, dalam + 1)]
    if t == "object" or "properties" in sch:
        return {k: contoh_nilai(v, komponen, dalam) for k, v in sch.get("properties", {}).items()}
    return contoh_nilai(sch, komponen, dalam)


def contoh_nilai(sch, komponen, dalam=0):
    if "$ref" in sch or sch.get("type") == "object" or "properties" in sch:
        return isi_dari_schema(sch, komponen, dalam + 1)
    if sch.get("enum"):
        return sch["enum"][0]
    t, f = sch.get("type"), sch.get("format")
    if t == "array":
        return []
    if t in ("integer", "number"):
        return 0
    if t == "boolean":
        return False
    if f == "uuid":
        return ""
    if f in ("date", "date-time"):
        return ""
    return ""


def main():
    spek = json.load(io.open(SPEK, encoding="utf-8"))
    komponen = spek.get("components", {}).get("schemas", {})

    folder = {}
    jumlah = 0
    for jalur, ops in spek["paths"].items():
        for verb, op in ops.items():
            if verb.upper() not in ("GET", "POST", "PUT", "PATCH", "DELETE"):
                continue
            jumlah += 1
            kunci = f"{verb.upper()} {jalur}"
            tag = (op.get("tags") or ["lain"])[0]

            # path variable -> nilai contoh yang mengacu variabel koleksi
            jalur_pm = jalur
            variabel = []
            for nama in re.findall(r"\{(\w+)\}", jalur):
                variabel.append({"key": nama, "value": PATH_VAR.get(nama, "")})
            segmen = [s for s in jalur_pm.strip("/").split("/")]
            segmen = [(":" + s[1:-1]) if s.startswith("{") else s for s in segmen]

            query = []
            for p in op.get("parameters", []):
                if p.get("in") == "query":
                    v = "{{workspaceId}}" if p["name"] == "workspaceId" else ""
                    query.append({"key": p["name"], "value": v,
                                  "description": p.get("description", ""),
                                  "disabled": not p.get("required", False) and not v})

            req = {
                "method": verb.upper(),
                "header": [],
                "url": {
                    "raw": "{{baseUrl}}/" + "/".join(segmen)
                           + ("?" + "&".join(f"{q['key']}={q['value']}" for q in query if not q.get("disabled")) if any(not q.get("disabled") for q in query) else ""),
                    "host": ["{{baseUrl}}"],
                    "path": segmen,
                },
                "description": op.get("summary") or op.get("description") or "",
            }
            if query:
                req["url"]["query"] = query
            if variabel:
                req["url"]["variable"] = variabel

            body = contoh_body(op, komponen)
            if body is not None:
                req["header"].append({"key": "Content-Type", "value": "application/json"})
                req["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2, ensure_ascii=False),
                               "options": {"raw": {"language": "json"}}}

            if kunci in TANPA_TOKEN:
                req["auth"] = {"type": "noauth"}

            item = {"name": f"{verb.upper()} {jalur}", "request": req, "response": []}
            if kunci in SIMPAN:
                item["event"] = [{"listen": "test", "script": {
                    "type": "text/javascript", "exec": skrip_simpan(SIMPAN[kunci])}}]

            folder.setdefault(tag, []).append(item)

    koleksi = {
        "info": {
            "_postman_id": str(uuid.uuid4()),
            "name": "DevFlow — API Lengkap",
            "description": (
                "Seluruh endpoint backend DevFlow.\n\n"
                "Dibangkitkan dari spesifikasi OpenAPI yang dihasilkan springdoc dari "
                "controller, jadi isinya mengikuti kode.\n\n"
                "CARA PAKAI\n"
                "1. Isi variabel `email` dan `password` dengan akun yang sudah didaftarkan.\n"
                "2. Jalankan `POST /api/v1/auth/login` — token tersimpan otomatis.\n"
                "3. Request lain sudah bisa dijalankan; id workspace/proyek/task ikut\n"
                "   tersimpan sendiri saat request yang membuatnya dijalankan.\n\n"
                "Semua request menembak api-gateway di :8080, jalur yang sama dengan "
                "yang dipakai aplikasi."
            ),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}]},
        "variable": [
            {"key": "baseUrl", "value": "http://localhost:8080"},
            {"key": "email", "value": "", "description": "Diisi sendiri — tidak disimpan di berkas ini"},
            {"key": "password", "value": "", "description": "Diisi sendiri — tidak disimpan di berkas ini"},
            {"key": "accessToken", "value": ""},
            {"key": "refreshToken", "value": ""},
            {"key": "userId", "value": ""},
            {"key": "workspaceId", "value": ""},
            {"key": "projectId", "value": ""},
            {"key": "taskId", "value": ""},
            {"key": "entryId", "value": ""},
            {"key": "sessionId", "value": ""},
            {"key": "invitationToken", "value": ""},
            {"key": "mediaId", "value": ""},
            {"key": "externalId", "value": ""},
            {"key": "revisionId", "value": ""},
        ],
        "item": [],
    }

    urut = ["auth-service", "workspace-service", "project-service",
            "integration-service", "billing-service"]
    for tag in urut + [t for t in folder if t not in urut]:
        if tag in folder:
            koleksi["item"].append({"name": tag, "item": sorted(folder[tag], key=lambda i: i["name"])})

    io.open(KELUARAN, "w", encoding="utf-8", newline="\n").write(
        json.dumps(koleksi, indent=2, ensure_ascii=False))

    isi = sum(len(f["item"]) for f in koleksi["item"])
    assert isi == jumlah, f"request di koleksi {isi} != operasi di spesifikasi {jumlah}"
    print(f"{KELUARAN}: {isi} request dalam {len(koleksi['item'])} folder "
          f"({os.path.getsize(KELUARAN) // 1024} KB)")


if __name__ == "__main__":
    main()
