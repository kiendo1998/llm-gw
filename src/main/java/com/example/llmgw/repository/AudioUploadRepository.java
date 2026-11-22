package com.example.llmgw.repository;

import com.example.llmgw.domain.AudioUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AudioUploadRepository extends JpaRepository<AudioUpload, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AudioUpload a where a.id = :id")
    Optional<AudioUpload> findByIdForUpdate(@Param("id") UUID id);
}
