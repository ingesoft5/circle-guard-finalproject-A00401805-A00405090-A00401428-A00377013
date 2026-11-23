/**
 * CG-DOC: HTTP controller for the file service: File Upload Controller.
 */
package com.circleguard.file.controller;

import com.circleguard.file.service.FileStorage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FileUploadController {
    // Upload counter tagged by result so Prometheus can alert on failures.
    private static final String UPLOAD_METRIC = "circleguard_file_uploads_total";
    private static final String TAG_RESULT = "result";

    // FileStorage is the port shared by the local and S3/MinIO adapters (HU-FILE-04).
    private final FileStorage storageService;
    private final MeterRegistry meterRegistry;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String filename = storageService.saveFile(file);
            meterRegistry.counter(UPLOAD_METRIC, TAG_RESULT, "success").increment();
            return ResponseEntity.ok(Map.of("filename", filename));
        } catch (RuntimeException e) {
            meterRegistry.counter(UPLOAD_METRIC, TAG_RESULT, "failure").increment();
            throw e;
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Resource resource = storageService.loadFile(filename);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> delete(@PathVariable String filename) {
        return storageService.deleteFile(filename)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}