package com.saas.project_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class TimeEntryRequest {

    /** Opsional: waktu boleh dicatat tanpa dikaitkan ke Flow tertentu — frontend
     *  menyediakan pilihan "Tanpa flow". Sebelumnya field ini @NotNull, yang akan
     *  menolak pilihan itu begitu controller mulai memakai @Valid. */
    private UUID taskId;

    @NotNull(message = "Tanggal entri wajib diisi")
    private LocalDate entryDate;

    @NotNull(message = "Durasi jam wajib diisi")
    @Positive(message = "Durasi jam harus lebih dari 0")
    @DecimalMax(value = "24.0", message = "Durasi satu entri maksimal 24 jam")
    private BigDecimal hours;

    @Size(max = 2000, message = "Catatan maksimal 2000 karakter")
    private String description;

    @NotBlank(message = "Jenis pekerjaan wajib diisi")
    private String entryType;

    @Size(max = 255, message = "Nama fitur maksimal 255 karakter")
    private String featureName;
}