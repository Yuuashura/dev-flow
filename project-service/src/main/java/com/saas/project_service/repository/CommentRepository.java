package com.saas.project_service.repository;

import com.saas.project_service.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByProjectIdAndEntityTypeAndEntityIdAndInternalFalseOrderByCreatedAtAsc(
            UUID projectId, String entityType, UUID entityId);
    List<Comment> findByProjectIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
            UUID projectId, String entityType, UUID entityId);

    /** Urutan DESC dengan Pageable: N komentar TERBARU diambil lewat LIMIT di SQL.
     *  Varian ASC di atas harus membaca seluruh percakapan lebih dulu untuk sampai ke
     *  ujungnya — pada tiket dengan ribuan komentar, itu seluruh tabel demi 200 baris.
     *  Pemanggil membalik hasilnya kembali ke urutan kronologis. */
    List<Comment> findByProjectIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID projectId, String entityType, UUID entityId, Pageable pageable);

    List<Comment> findByProjectIdAndEntityTypeAndEntityIdAndInternalFalseOrderByCreatedAtDesc(
            UUID projectId, String entityType, UUID entityId, Pageable pageable);
}