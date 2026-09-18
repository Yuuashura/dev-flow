package com.saas.auth_service.controller;

import com.saas.auth_service.entity.MediaAsset;
import com.saas.auth_service.entity.MediaKind;
import com.saas.auth_service.security.UserPrincipal;
import com.saas.auth_service.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Mengunggah gambar. Mengembalikan id dan path untuk disimpan di kolom
     * avatar_url / logo_url.
     *
     * <p>Parameter `kind` dibatasi enum, jadi nilai di luar daftar ditolak binder
     * sebagai 400 sebelum menyentuh service.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("kind") MediaKind kind,
            @RequestPart("file") MultipartFile file) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tidak terautentikasi");
        }
        MediaAsset asset = mediaService.store(principal.getId(), kind, file);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", asset.getId().toString(),
                "url", "/api/v1/media/" + asset.getId(),
                "contentType", asset.getContentType(),
                "width", asset.getWidth(),
                "height", asset.getHeight(),
                "sizeBytes", asset.getSizeBytes()
        ));
    }

    /**
     * Menyajikan byte gambar.
     *
     * <p>Terbuka tanpa autentikasi dengan sengaja: avatar muncul di daftar anggota
     * dan komentar, jadi memerlukannya token akan memecah tampilan untuk semua orang.
     * Id-nya UUID acak, dan isinya sudah dijamin raster oleh encode ulang saat unggah —
     * tidak ada yang bocor selain gambar yang memang dipasang orang di profilnya.
     *
     * <p>Header di bawah bukan hiasan:
     * <ul>
     *   <li><b>nosniff</b> — tanpa itu browser boleh menebak tipe dari isi dan
     *       menjalankan berkas yang kita sajikan sebagai gambar sebagai sesuatu yang
     *       lain. Ini pertahanan terakhir kalau validasi unggah pernah jebol.</li>
     *   <li><b>Content-Disposition: inline dengan nama file tetap</b> — nama dari
     *       klien tidak pernah ikut, jadi tidak ada jalan menyelipkan karakter
     *       kendali atau ekstensi ganda ke header.</li>
     *   <li><b>Content-Security-Policy: sandbox</b> — kalaupun sesuatu berhasil
     *       tersaji sebagai dokumen, ia berjalan tanpa origin, tanpa skrip.</li>
     * </ul>
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> serve(
            @PathVariable UUID id,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        MediaAsset asset = mediaService.get(id);
        String etag = "\"" + asset.getSha256() + "\"";

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .eTag(etag)
                // Isi tidak pernah berubah untuk id yang sama: unggahan baru selalu
                // menghasilkan id baru.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"image\"")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(asset.getData());
    }
}
