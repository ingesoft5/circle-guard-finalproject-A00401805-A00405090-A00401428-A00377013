/**
 * CG-DOC: Unit tests for File Storage Service (FS-CI-01).
 */
package com.circleguard.file.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void defaultConstructorCreatesUploadsDirectory() throws IOException {
        Path uploads = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            FileStorageService defaultService = new FileStorageService();

            assertNotNull(defaultService);
            assertTrue(Files.isDirectory(uploads));
        } finally {
            if (Files.isDirectory(uploads)) {
                try (Stream<Path> entries = Files.list(uploads)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(uploads);
                    }
                }
            }
        }
    }

    @Test
    void stringConstructorUsesConfiguredPath() throws IOException {
        Path configured = tempDir.resolve("configured-uploads");
        FileStorageService configuredService = new FileStorageService(configured.toString());

        assertTrue(Files.isDirectory(configured));
        String filename = configuredService.saveFile(new MockMultipartFile(
                "file", "configured.txt", "text/plain", "ok".getBytes()));
        assertTrue(Files.exists(configured.resolve(filename)));
    }

    @Test
    void constructorWrapsDirectoryCreationFailure() throws IOException {
        Path blocker = Files.createFile(tempDir.resolve("not-a-directory"));
        Path invalidRoot = blocker.resolve("child");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> new FileStorageService(invalidRoot));

        assertEquals("Could not initialize storage", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void saveFileUsesDefaultNameWhenOriginalFilenameIsNull() {
        MockMultipartFile file = new MockMultipartFile("file", null, "text/plain", "content".getBytes());

        String filename = service.saveFile(file);

        assertTrue(filename.endsWith("_file"));
    }

    @Test
    void saveFileUsesDefaultNameWhenOriginalFilenameIsBlank() {
        MockMultipartFile file = new MockMultipartFile("file", "   ", "text/plain", "content".getBytes());

        String filename = service.saveFile(file);

        assertTrue(filename.endsWith("_file"));
    }

    @Test
    void saveFileStripsWindowsPathFromOriginalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\temp\\report.pdf", "application/pdf", "content".getBytes());

        String filename = service.saveFile(file);

        assertTrue(filename.endsWith("_report.pdf"));
        assertFalse(filename.contains("\\"));
    }

    @Test
    void saveFileRethrowsRuntimeExceptionFromStorage() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("broken.pdf");
        when(file.getInputStream()).thenThrow(new IllegalStateException("disk failure"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.saveFile(file));

        assertEquals("disk failure", ex.getMessage());
    }

    @Test
    void saveFileWrapsCheckedExceptionFromStorage() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("broken.pdf");
        when(file.getInputStream()).thenThrow(new IOException("disk failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.saveFile(file));

        assertEquals("Could not store file", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void loadFileReturnsNullForInvalidPath() {
        assertNull(service.loadFile("invalid\0name"));
    }
}
