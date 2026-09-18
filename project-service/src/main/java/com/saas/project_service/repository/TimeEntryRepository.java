package com.saas.project_service.repository;

import com.saas.project_service.entity.TimeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    List<TimeEntry> findByProjectIdOrderByEntryDateDescCreatedAtDesc(UUID projectId);

    Page<TimeEntry> findByProjectIdOrderByEntryDateDescCreatedAtDesc(UUID projectId, Pageable pageable);

    List<TimeEntry> findByProjectIdAndUserIdOrderByEntryDateDescCreatedAtDesc(UUID projectId, UUID userId);

    List<TimeEntry> findByProjectIdAndEntryDateBetweenOrderByEntryDateDesc(
            UUID projectId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t WHERE t.projectId = :projectId")
    BigDecimal sumHoursByProject(@Param("projectId") UUID projectId);

    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t WHERE t.projectId = :projectId AND t.userId = :userId")
    BigDecimal sumHoursByProjectAndUser(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t WHERE t.projectId = :projectId AND t.entryDate BETWEEN :from AND :to")
    BigDecimal sumHoursByProjectAndDateRange(@Param("projectId") UUID projectId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t WHERE t.projectId = :projectId AND t.userId = :userId AND t.entryDate BETWEEN :from AND :to")
    BigDecimal sumHoursByProjectAndUserAndDateRange(@Param("projectId") UUID projectId, @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<TimeEntry> findByProjectIdAndTaskIdOrderByEntryDateDesc(UUID projectId, UUID taskId);
}