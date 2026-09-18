package com.saas.project_service.dto;

import com.saas.project_service.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateTaskRequest {
    @NotBlank
    private String title;

    private String description;
    private TaskPriority priority;
    private UUID assignedTo;
    private LocalDate startDate;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
}
