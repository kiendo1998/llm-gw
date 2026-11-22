package com.example.llmgw.api.audio;

import com.example.llmgw.service.AudioStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioStreamController {
    private final AudioStorageService audioStorage;

    public AudioStreamController(AudioStorageService audioStorage) { this.audioStorage = audioStorage; }

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    AudioStreamResponse upload(
            @RequestHeader(value = "X-Audio-Id", required = false) UUID audioId,
            @RequestHeader(value = "X-File-Name", required = false) String originalFilename,
            @RequestHeader(value = "X-Content-Type", required = false) String contentType,
            @RequestHeader("X-Chunk-Sequence") int sequence,
            @RequestHeader(value = "X-Upload-Complete", defaultValue = "false") boolean complete,
            HttpServletRequest request) throws IOException {
        if (audioId == null) {
            if (sequence != 0) throw new IllegalArgumentException("First chunk sequence must be 0");
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new IllegalArgumentException("X-File-Name is required for the first chunk");
            }
            audioId = audioStorage.create(originalFilename, contentType).getId();
        }
        var chunk = audioStorage.storeChunk(audioId, sequence, request.getInputStream());
        var upload = complete ? audioStorage.complete(audioId, sequence) : audioStorage.get(audioId);
        return AudioStreamResponse.from(upload, sequence, chunk.sizeBytes(), chunk.duplicate());
    }
}
