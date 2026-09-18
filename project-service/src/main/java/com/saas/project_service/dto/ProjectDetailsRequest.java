package com.saas.project_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectDetailsRequest {
    @NotBlank
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate targetDate;
}
