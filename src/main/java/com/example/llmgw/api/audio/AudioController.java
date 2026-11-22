package com.example.llmgw.api.audio;

import com.example.llmgw.service.AudioStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {
    private final AudioStorageService audioStorage;

    public AudioController(AudioStorageService audioStorage) { this.audioStorage = audioStorage; }

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AudioUploadResult> upload(
            @RequestParam(name = "async", defaultValue = "false") boolean async,
            @RequestHeader(value = "X-Audio-Id", required = false) UUID requestedAudioId,
            @RequestHeader("X-File-Name") String originalFilename,
            @RequestHeader(value = "X-Content-Type", defaultValue = "application/octet-stream") String contentType,
            @RequestHeader(value = "X-Chunk-Sequence", defaultValue = "0") int sequence,
            @RequestHeader(value = "X-Total-Chunks", defaultValue = "1") int totalChunks,
            HttpServletRequest request) throws IOException {
        var upload = audioStorage.createOrGet(requestedAudioId, originalFilename, contentType, totalChunks);
        var stored = audioStorage.storeChunk(upload.getId(), sequence, request.getInputStream());
        var progress = audioStorage.progress(upload.getId());
        upload = progress.upload();
        if (upload.getStatus() == com.example.llmgw.domain.AudioUploadStatus.UPLOADING && progress.complete()) {
            upload = async ? audioStorage.enqueue(upload.getId())
                    : audioStorage.complete(upload.getId(), totalChunks - 1);
            progress = audioStorage.progress(upload.getId());
        }
        var response = AudioUploadResult.from(upload, progress.nextSequence(), async,
                sequence, stored.duplicate());
        if (!progress.complete() || async) return ResponseEntity.accepted().body(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{audioId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    AudioStatusResponse status(@PathVariable UUID audioId) {
        var progress = audioStorage.progress(audioId);
        return AudioStatusResponse.from(progress.upload(), progress.nextSequence());
    }

    @GetMapping("/{audioId}/download")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID audioId) throws IOException {
        var upload = audioStorage.get(audioId);
        var path = audioStorage.completedFile(upload);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(upload.getContentType()))
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(upload.getOriginalFilename()).build().toString())
                .body(new InputStreamResource(Files.newInputStream(path)));
    }
}
