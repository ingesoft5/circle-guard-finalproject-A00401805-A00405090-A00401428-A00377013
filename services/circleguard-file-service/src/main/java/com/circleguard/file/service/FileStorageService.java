/**
 * CG-DOC: local filesystem storage adapter for the file service; active when file.storage.mode=local.
 */
package com.circleguard.file.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Default {@link FileStorage} implementation: stores objects under a local directory. */
@Service
@ConditionalOnProperty(name = "file.storage.mode", havingValue = "local", matchIfMissing = true)
public class FileStorageService implements FileStorage {
    private final Path root;

    public FileStorageService() {
        this(Paths.get("uploads"));
    }

    @Autowired
    public FileStorageService(@Value("${file.storage.path:uploads}") String path) {
        this(Paths.get(path));
    }

    public FileStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    @Override
    public String saveFile(MultipartFile file) {
        String filename = FileStorage.generateFilename(file.getOriginalFilename());
        try {
            Path destination = this.root.resolve(filename).normalize();
            if (!destination.startsWith(this.root)) {
                throw new RuntimeException("Cannot store file outside storage directory");
            }
            Files.copy(file.getInputStream(), destination);
            return filename;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    @Override
    public Resource loadFile(String filename) {
        if (!FileStorage.isValidFilename(filename)) {
            return null;
        }
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

    @Override
    public boolean deleteFile(String filename) {
        if (!FileStorage.isValidFilename(filename)) {
            return false;
        }
        try {
            Path file = this.root.resolve(filename).normalize();
            if (!file.startsWith(this.root)) {
                return false;
            }
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete file", e);
        }
    }
}
