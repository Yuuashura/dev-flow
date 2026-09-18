package com.saas.integration_service.repository;

import com.saas.integration_service.entity.NormalizedGithubActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NormalizedGithubActivityRepository extends JpaRepository<NormalizedGithubActivity, UUID> {
    List<NormalizedGithubActivity> findByProjectId(UUID projectId);

    /** Batas diturunkan ke query (LIMIT di SQL), bukan dipotong setelah baris terbaca.
     *  Memotong di memori tetap menarik seluruh tabel lewat jaringan dan memetakannya
     *  ke entity — persis biaya yang ingin dihindari. */
    List<NormalizedGithubActivity> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
