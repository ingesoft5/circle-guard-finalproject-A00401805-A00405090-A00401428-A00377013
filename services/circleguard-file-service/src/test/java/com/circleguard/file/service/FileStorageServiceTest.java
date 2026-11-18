/**
 * CG-DOC: Unit tests for File Storage Service (FS-CI-01).
 */
package com.circleguard.file.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private final FileStorageService service = new FileStorageService();

    @AfterEach
    void cleanupUploads() throws IOException {
        java.io.File uploads = new java.io.File("uploads");
        if (uploads.isDirectory()) {
            java.io.File[] files = uploads.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    Files.deleteIfExists(file.toPath());
                }
            }
        }
    }

    @Test
    void shouldSaveValidFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "mock content".getBytes());

        String filename = service.saveFile(file);

        assertNotNull(filename);
        assertTrue(filename.endsWith("_certificate.pdf"));
        assertTrue(Files.exists(Path.of("uploads", filename)));
        assertEquals("mock content", new String(Files.readAllBytes(Path.of("uploads", filename))));
    }

    @Test
    void shouldSaveEmptyFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        String filename = service.saveFile(file);

        assertNotNull(filename);
        Path stored = Path.of("uploads", filename);
        assertTrue(Files.exists(stored));
        assertEquals(0, stored.toFile().length());
    }

    @Test
    void shouldLoadExistingFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello world".getBytes());
        String filename = service.saveFile(file);

        Resource resource = service.loadFile(filename);

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        assertEquals("hello world", new String(resource.getInputStream().readAllBytes()));
    }

    @Test
    void shouldReturnNullForNonExistingFile() {
        Resource resource = service.loadFile("no-such-file.txt");

        assertNull(resource);
    }

    @Test
    void shouldReturnNullForPathTraversal() {
        Resource resource = service.loadFile("../etc/passwd");

        assertNull(resource);
    }

    @Test
    void loadFileOnEmptyFileReturnsResource() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);
        String filename = service.saveFile(file);

        Resource resource = service.loadFile(filename);

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertEquals(0, resource.contentLength());
    }
}
