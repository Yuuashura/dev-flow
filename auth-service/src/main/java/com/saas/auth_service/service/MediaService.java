package com.saas.auth_service.service;

import com.saas.auth_service.entity.MediaAsset;
import com.saas.auth_service.entity.MediaKind;
import com.saas.auth_service.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.UUID;

/**
 * Menerima gambar unggahan dan menyimpan byte-nya.
 *
 * <p>Aturan utamanya: <b>tidak ada satu pun keputusan yang diambil dari apa yang
 * dikirim klien</b>. Nama file, ekstensi, dan header Content-Type semuanya diabaikan —
 * ketiganya dipilih penyerang. Tipe ditentukan dari byte pertama berkas, isinya harus
 * benar-benar bisa di-decode sebagai gambar, lalu di-encode ulang sehingga yang
 * tersimpan hanya piksel.
 *
 * <p>Encode ulang itu yang menutup seluruh kelas serangan sekaligus:
 * <ul>
 *   <li>File polyglot (satu berkas yang sah sebagai GIF sekaligus HTML atau JAR)
 *       tidak selamat — keluarannya ditulis ulang dari raster.</li>
 *   <li>Muatan yang ditempel setelah akhir data gambar ikut hilang.</li>
 *   <li>Metadata EXIF — termasuk koordinat GPS tempat foto diambil — tidak ikut
 *       tersalin. Pengguna jarang sadar foto profilnya membawa itu.</li>
 *   <li>SVG ditolak seluruhnya: ia dokumen XML yang bisa memuat skrip, bukan raster.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;

    /** 2 MB. Cukup untuk foto profil, dan membatasi biaya decode per permintaan. */
    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    /** Batas piksel sebelum decode penuh. Berkas 10 KB bisa mendeklarasikan
     *  50000x50000 piksel; meng-alokasikannya butuh puluhan gigabita RAM dan
     *  mematikan service. Dimensi dibaca dari header dulu, lalu dibandingkan ke sini,
     *  sebelum satu piksel pun dialokasikan. */
    public static final int MAX_DIMENSION = 4096;

    /** Sisi terpanjang hasil simpan. Avatar 4096px tidak ada gunanya dan membuat
     *  setiap pemuatan halaman mahal. */
    public static final int MAX_STORED_DIMENSION = 512;

    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";

    @Transactional
    public MediaAsset store(UUID ownerUserId, MediaKind kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Berkas gambar wajib diunggah.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Ukuran gambar maksimal " + (MAX_UPLOAD_BYTES / 1024 / 1024) + " MB.");
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Berkas gagal dibaca.", e);
        }

        // Tipe ditentukan dari byte, bukan dari klaim klien.
        String detected = sniff(raw);
        if (detected == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Format gambar tidak didukung. Gunakan PNG atau JPG.");
        }

        BufferedImage image = decodeWithBounds(raw);
        BufferedImage resized = downscale(image);

        // Encode ulang: sejak titik ini isinya hanya piksel.
        String outputType = PNG.equals(detected) ? PNG : JPEG;
        byte[] encoded = encode(resized, outputType);

        MediaAsset asset = MediaAsset.builder()
                .ownerUserId(ownerUserId)
                .kind(kind)
                .contentType(outputType)
                .sizeBytes(encoded.length)
                .width(resized.getWidth())
                .height(resized.getHeight())
                .sha256(sha256Hex(encoded))
                .data(encoded)
                .build();

        return mediaAssetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public MediaAsset get(UUID id) {
        return mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gambar tidak ditemukan."));
    }

    /**
     * Mengenali tipe dari byte pertama berkas.
     *
     * <p>Hanya PNG dan JPEG yang diterima. GIF ditolak karena varian animasinya tidak
     * selamat melewati encode ulang, dan format itu punya riwayat panjang sebagai
     * pembawa polyglot. SVG ditolak karena bukan raster sama sekali — ia XML yang bisa
     * memuat &lt;script&gt;, dan menyajikannya dari origin kita berarti XML itu berjalan
     * dengan hak penuh sesi pengguna.
     */
    static String sniff(byte[] b) {
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        return null;
    }

    /**
     * Membaca dimensi dari header lebih dulu, menolak yang kelewat besar, baru
     * men-decode. Urutannya penting: decode duluan berarti alokasi memori sudah terjadi
     * sebelum kita sempat menolaknya.
     */
    private BufferedImage decodeWithBounds(byte[] raw) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Berkas bukan gambar yang valid.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Berkas bukan gambar yang valid.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Dimensi gambar maksimal " + MAX_DIMENSION + "x" + MAX_DIMENSION + " piksel.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gambar gagal dibaca.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Berkas yang rusak atau sengaja dibuat cacat mendarat di sini. Itu
            // kesalahan masukan, bukan kegagalan server.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gambar gagal dibaca.", e);
        }
    }

    /** Memperkecil sisi terpanjang ke MAX_STORED_DIMENSION, menjaga rasio. */
    private BufferedImage downscale(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);

        int targetW = w;
        int targetH = h;
        if (longest > MAX_STORED_DIMENSION) {
            double scale = (double) MAX_STORED_DIMENSION / longest;
            targetW = Math.max(1, (int) Math.round(w * scale));
            targetH = Math.max(1, (int) Math.round(h * scale));
        }

        // TYPE_INT_RGB, bukan ARGB: JPEG tidak punya kanal alfa, dan menulis gambar
        // beralfa ke JPEG menghasilkan warna kacau. Latar putih dipakai untuk area
        // yang sebelumnya transparan.
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);
            g.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] encode(BufferedImage image, String contentType) {
        String format = PNG.equals(contentType) ? "png" : "jpg";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Gagal menyimpan gambar.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal menyimpan gambar.", e);
        }
        return out.toByteArray();
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 wajib tersedia di setiap JVM", e);
        }
    }
}
