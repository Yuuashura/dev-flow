package com.saas.project_service.repository;

import com.saas.project_service.entity.Task;
import com.saas.project_service.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByProjectIdOrderByPositionAscCreatedAtAsc(UUID projectId);
    List<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status);
    long countByProjectId(UUID projectId);
    long countByProjectIdAndStatus(UUID projectId, TaskStatus status);
    long countByProjectIdAndAssignedTo(UUID projectId, UUID userId);
    long countByProjectIdAndAssignedToAndStatus(UUID projectId, UUID userId, TaskStatus status);
    long countByProjectIdAndDueDateBeforeAndStatusNot(UUID projectId, java.time.LocalDate date, TaskStatus status);
}
