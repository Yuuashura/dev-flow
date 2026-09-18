package com.saas.integration_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Ketiga field disambung langsung ke path/query api.github.com dan disimpan ke kolom
 * varchar(255). Pola di bawah mengikuti aturan GitHub sendiri, jadi nilai yang lolos
 * tidak bisa menyelipkan segmen path, query, atau karakter yang butuh encoding.
 */
@Data
public class LinkRepoRequest {

    /** GitHub: alfanumerik dan tanda hubung, maksimal 39 karakter, tidak diawali/diakhiri '-'. */
    @NotBlank(message = "Owner repository wajib diisi")
    @Size(max = 39, message = "Owner repository maksimal 39 karakter")
    @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$",
            message = "Owner repository hanya boleh huruf, angka, dan tanda hubung")
    private String owner;

    /** GitHub: alfanumerik, titik, garis bawah, tanda hubung. Maksimal 100 karakter. */
    @NotBlank(message = "Nama repository wajib diisi")
    @Size(max = 100, message = "Nama repository maksimal 100 karakter")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "Nama repository hanya boleh huruf, angka, titik, garis bawah, dan tanda hubung")
    private String repo;

    /**
     * Opsional — kalau kosong, default branch repo yang dipakai. Kolom
     * projects.github_repo_branch adalah VARCHAR(255); sebelumnya field ini tidak punya
     * anotasi sama sekali, jadi tidak ada apa pun antara request dan driver Postgres.
     * Pola ini menolak spasi, '..', dan karakter yang dilarang git ref.
     */
    @Size(max = 255, message = "Nama branch maksimal 255 karakter")
    @Pattern(regexp = "^[A-Za-z0-9._/-]+$",
            message = "Nama branch hanya boleh huruf, angka, titik, garis miring, garis bawah, dan tanda hubung")
    private String branch;
}
