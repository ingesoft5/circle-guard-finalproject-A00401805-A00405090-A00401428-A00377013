/**
 * CG-DOC: Unit tests for File Storage Service (FS-CI-01).
 */
package com.circleguard.file.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(tempDir);
    }

    @Test
    void shouldSaveValidFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "mock content".getBytes());

        String filename = service.saveFile(file);

        assertNotNull(filename);
        assertTrue(filename.endsWith("_certificate.pdf"));
        Path stored = tempDir.resolve(filename);
        assertTrue(Files.exists(stored));
        assertEquals("mock content", Files.readString(stored));
    }

    @Test
    void shouldSaveEmptyFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        String filename = service.saveFile(file);

        assertNotNull(filename);
        Path stored = tempDir.resolve(filename);
        assertTrue(Files.exists(stored));
        assertEquals(0, Files.size(stored));
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

    @Test
    void shouldNotStoreFileOutsideRootWhenFilenameContainsTraversal() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../../tmp/cg-escape-test.txt", "text/plain", "malicious".getBytes());

        String filename = service.saveFile(file);

        assertNotNull(filename);
        assertFalse(filename.contains(".."));
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("\\"));

        Path stored = tempDir.resolve(filename).normalize();
        assertTrue(Files.exists(stored));
        assertTrue(stored.startsWith(tempDir.toAbsolutePath().normalize()));
        assertEquals("malicious", Files.readString(stored));

        Path escaped = tempDir.toAbsolutePath().normalize().getParent().resolve("cg-escape-test.txt");
        assertFalse(Files.exists(escaped), "File must not be written outside the storage directory");
    }

    @Test
    void shouldDeleteExistingFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "delete-me.txt", "text/plain", "content".getBytes());
        String filename = service.saveFile(file);

        assertTrue(service.deleteFile(filename));
        assertNull(service.loadFile(filename));
        assertFalse(Files.exists(tempDir.resolve(filename)));
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingFile() {
        assertFalse(service.deleteFile("no-such-file.txt"));
    }

    @Test
    void shouldRejectTraversalOnDelete() {
        assertFalse(service.deleteFile("../etc/passwd"));
    }
}
