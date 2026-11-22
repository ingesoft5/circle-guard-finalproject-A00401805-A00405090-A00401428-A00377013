/**
 * CG-DOC: Unit tests for the real S3/MinIO storage adapter (HU-FILE-04).
 */
package com.circleguard.file.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3FileStorageServiceTest {

    private static final String BUCKET = "circleguard-files";

    private S3Client s3Client;
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        s3Client = Mockito.mock(S3Client.class);
        service = new S3FileStorageService(s3Client, BUCKET);
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void shouldSaveToConfiguredBucketAndReturnGeneratedKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "s3 payload".getBytes());

        String key = service.saveFile(file);

        assertTrue(key.endsWith("_certificate.pdf"));
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(key, request.getValue().key());
    }

    @Test
    void shouldLoadExistingObjectAsNamedResource() throws IOException {
        byte[] content = "downloaded certificate".getBytes();
        Mockito.when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseBytes(content));

        Resource resource = service.loadFile("uuid_certificate.pdf");

        assertNotNull(resource);
        assertEquals("uuid_certificate.pdf", resource.getFilename());
        assertArrayEquals(content, resource.getContentAsByteArray());
    }

    @Test
    void shouldReturnNullForMissingObject() {
        Mockito.when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(notFound());

        assertNull(service.loadFile("missing.pdf"));
    }

    @Test
    void shouldDeleteExistingObject() {
        Mockito.when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertTrue(service.deleteFile("uuid_certificate.pdf"));

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("uuid_certificate.pdf", request.getValue().key());
    }

    @Test
    void shouldNotDeleteMissingObject() {
        Mockito.when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound());

        assertFalse(service.deleteFile("missing.pdf"));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldRejectInvalidKeysWithoutCallingS3() {
        assertNull(service.loadFile("../etc/passwd"));
        assertFalse(service.deleteFile("uploads/file.pdf"));
        verifyNoInteractions(s3Client);
    }

    private static ResponseBytes<GetObjectResponse> responseBytes(byte[] content) {
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content);
    }

    private static S3Exception notFound() {
        return (S3Exception) S3Exception.builder().statusCode(404).message("Not Found").build();
    }
}
