package com.saas.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Nama lengkap wajib diisi")
    @Size(max = 150)
    private String fullName;

    @Size(max = 30)
    private String phoneNumber;

    @Size(max = 100)
    private String jobTitle;

    @Size(max = 100)
    private String companyName;

    @Size(max = 100)
    private String city;

    @Size(max = 1000)
    private String bio;

    @Size(max = 1000)
    private String avatarUrl;
}
