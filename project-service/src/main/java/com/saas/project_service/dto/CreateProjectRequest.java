package com.saas.project_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateProjectRequest {
    @NotNull
    private UUID workspaceId;

    @NotBlank
    private String name;

    private String description;
    private LocalDate startDate;
    private LocalDate targetDate;
    private boolean clientVisible = true;
}
