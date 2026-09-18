package com.saas.project_service.dto;

import com.saas.project_service.entity.TaskPriority;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

/** Semua field opsional. Field yang null diabaikan, kecuali assignedTo dan dueDate
 *  yang memakai flag terpisah agar bisa dikosongkan secara eksplisit. */
@Data
public class UpdateTaskRequest {
    private String title;
    private String description;
    private TaskPriority priority;
    private UUID assignedTo;
    private LocalDate startDate;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private boolean clearAssignee;
    private boolean clearStartDate;
    private boolean clearDueDate;
    private boolean clearEstimate;
}
