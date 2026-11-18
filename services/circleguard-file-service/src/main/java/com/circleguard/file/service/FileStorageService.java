/**
 * CG-DOC: business service for the file service: File Storage Service.
 */
package com.circleguard.file.service;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path root = Paths.get("uploads");

    public FileStorageService() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String saveFile(MultipartFile file) {
        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        try {
            Files.copy(file.getInputStream(), this.root.resolve(filename));
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    public Resource loadFile(String filename) {
        try {
            Path file = this.root.resolve(filename).normalize();
            if (!file.startsWith(this.root)) {
                return null;
            }
            Resource resource = new PathResource(file);
            return resource.exists() && resource.isReadable() ? resource : null;
        } catch (Exception e) {
            return null;
        }
    }
}