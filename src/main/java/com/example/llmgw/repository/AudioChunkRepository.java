package com.example.llmgw.repository;

import com.example.llmgw.domain.AudioChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface AudioChunkRepository extends JpaRepository<AudioChunk, UUID> {
    Optional<AudioChunk> findByUploadIdAndSequenceNumber(UUID uploadId, int sequenceNumber);
    List<AudioChunk> findByUploadIdOrderBySequenceNumber(UUID uploadId);
}
