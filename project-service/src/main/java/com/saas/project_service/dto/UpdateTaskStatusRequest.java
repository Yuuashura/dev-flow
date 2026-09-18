package com.saas.project_service.dto;

import com.saas.project_service.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Status dan alasannya datang bersama, sengaja.
 *
 * <p>Sebelumnya status dikirim sebagai query param dan alasannya ditulis frontend lewat
 * panggilan POST /comments yang terpisah. Dua panggilan berarti dua hal yang bisa gagal
 * sendiri-sendiri: kalau yang kedua gagal, tahapnya sudah berpindah tanpa jejak alasan
 * sama sekali. Dan aturan "alasan wajib" itu hanya ada di komponen React — siapa pun
 * yang memanggil API langsung melewatinya.
 *
 * <p>Dengan keduanya di satu body, keduanya masuk satu transaksi: tersimpan bersama
 * atau sama-sama gagal.
 */
@Data
public class UpdateTaskStatusRequest {

    @NotNull(message = "Status tujuan wajib diisi.")
    private TaskStatus status;

    @NotBlank(message = "Alasan perpindahan tahap wajib diisi.")
    @Size(min = 3, max = 500, message = "Alasan harus 3 sampai 500 karakter.")
    private String reason;
}
