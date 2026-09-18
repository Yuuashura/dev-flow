package com.saas.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Password saat ini wajib diisi")
    private String currentPassword;

    @NotBlank(message = "Password baru wajib diisi")
    @Size(min = 8, message = "Password baru minimal 8 karakter")
    private String newPassword;

    @NotBlank(message = "Konfirmasi password wajib diisi")
    private String confirmPassword;

    /**
     * Refresh token milik perangkat yang sedang dipakai. Semua sesi lain dicabut
     * setelah password berubah; tanpa ini pengguna ikut ter-logout dari perangkat
     * yang baru saja dia pakai untuk mengganti password.
     */
    private String currentRefreshToken;
}
