/**
 * CG-DOC: Storage port implemented by the local filesystem and S3/MinIO adapters (HU-FILE-04).
 */
package com.circleguard.file.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.UUID;

/**
 * Contract used by {@code FileUploadController}. Implementations must generate the same
 * storage key on save and accept that key on download/delete, regardless of the backend.
 */
public interface FileStorage {

    /** Persists a multipart file and returns the generated storage key. */
    String saveFile(MultipartFile file);

    /** @return the stored resource, or {@code null} when the key is unknown/invalid. */
    Resource loadFile(String filename);

    /** @return {@code true} when the object existed and was removed. */
    boolean deleteFile(String filename);

    /** Builds a traversal-safe key by keeping only the sanitized original file name. */
    static String generateFilename(String originalFilename) {
        String safeOriginalName = originalFilename == null
                ? "file"
                : Paths.get(originalFilename.replace('\\', '/')).getFileName().toString();
        if (safeOriginalName.isBlank()) {
            safeOriginalName = "file";
        }
        return UUID.randomUUID() + "_" + safeOriginalName;
    }

    /** Rejects keys that could escape the storage namespace in either backend. */
    static boolean isValidFilename(String filename) {
        return filename != null && !filename.isBlank()
                && !filename.contains("/") && !filename.contains("\\")
                && !filename.contains("..");
    }
}
