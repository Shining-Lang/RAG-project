package com.lsn.ragkb.repository;

import com.lsn.ragkb.entity.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    List<KbDocument> findByKbIdAndIsDeletedFalse(Long kbId);

    boolean existsByKbIdAndFileNameAndIsDeletedFalse(Long kbId, String fileName);

    Optional<KbDocument> findByKbIdAndFileNameAndIsDeletedFalse(Long kbId, String fileName);

    @Query("SELECT COUNT(d) FROM KbDocument d WHERE d.status = :status")
    long countByStatus(KbDocument.DocumentStatus status);
}
