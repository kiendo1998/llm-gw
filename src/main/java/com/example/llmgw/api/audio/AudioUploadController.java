package com.example.llmgw.api.audio;

import com.example.llmgw.service.AudioStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audio-uploads")
public class AudioUploadController {
    private final AudioStorageService audioStorage;

    public AudioUploadController(AudioStorageService audioStorage) { this.audioStorage = audioStorage; }

    @PostMapping
    ResponseEntity<AudioUploadResponse> create(@Valid @RequestBody CreateAudioUploadRequest request) {
        var upload = audioStorage.create(request.originalFilename(), request.contentType());
        return ResponseEntity.created(URI.create("/api/v1/audio-uploads/" + upload.getId()))
                .body(AudioUploadResponse.from(upload));
    }

    @PutMapping(value = "/{audioId}/chunks/{sequence}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    AudioChunkResponse uploadChunk(@PathVariable UUID audioId, @PathVariable int sequence,
                                   HttpServletRequest request) throws IOException {
        return audioStorage.storeChunk(audioId, sequence, request.getInputStream());
    }

    @PostMapping("/{audioId}/complete")
    AudioUploadResponse complete(@PathVariable UUID audioId,
                                 @Valid @RequestBody CompleteAudioUploadRequest request) {
        return AudioUploadResponse.from(audioStorage.complete(audioId, request.lastSequence()));
    }

    @GetMapping("/{audioId}")
    AudioUploadResponse get(@PathVariable UUID audioId) {
        return AudioUploadResponse.from(audioStorage.get(audioId));
    }

    @GetMapping("/{audioId}/content")
    ResponseEntity<InputStreamResource> content(@PathVariable UUID audioId) throws IOException {
        var upload = audioStorage.get(audioId);
        var path = audioStorage.completedFile(upload);
        var mediaType = MediaType.parseMediaType(upload.getContentType());
        var resource = new InputStreamResource(Files.newInputStream(path));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(upload.getOriginalFilename()).build().toString())
                .body(resource);
    }
}
