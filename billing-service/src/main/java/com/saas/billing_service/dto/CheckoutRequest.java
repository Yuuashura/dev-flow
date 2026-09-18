package com.saas.billing_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]{2,100}$")
    private String workspaceSlug;

    @NotBlank
    @Size(max = 150)
    private String customerName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String customerEmail;

    @Size(max = 30)
    private String customerPhone;

    @Size(max = 100)
    private String companyName;
}
