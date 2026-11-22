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
            @RequestHeader("X-File-Name") String originalFilename,
            @RequestHeader(value = "X-Content-Type", defaultValue = "application/octet-stream") String contentType,
            HttpServletRequest request) throws IOException {
        var intake = audioStorage.createOrGetByPartFilename(originalFilename, contentType);
        var upload = intake.upload();
        var stored = audioStorage.storePart(upload.getId(), intake.sequence(), request.getInputStream());
        upload = async ? audioStorage.enqueue(upload.getId()) : audioStorage.completePending(upload.getId());
        var response = AudioUploadResult.from(upload, stored.sequence() + 1, stored.duplicate(), async);
        return async ? ResponseEntity.accepted().body(response) : ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{audioId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    AudioStatusResponse status(@PathVariable String audioId) {
        return AudioStatusResponse.from(audioStorage.getByIdOrSession(audioId));
    }

    @GetMapping("/{audioId}/download")
    ResponseEntity<InputStreamResource> download(@PathVariable String audioId) throws IOException {
        var upload = audioStorage.getByIdOrSession(audioId);
        var path = audioStorage.completedFile(upload);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(upload.getContentType()))
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(upload.getOriginalFilename()).build().toString())
                .body(new InputStreamResource(Files.newInputStream(path)));
    }
}
