/**
 * CG-DOC: Integration tests for the real S3/MinIO file-storage adapter (HU-FILE-04).
 */
package com.circleguard.file.integration;

import com.circleguard.file.service.FileStorage;
import com.circleguard.file.service.S3FileStorageService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the real {@code FileUploadController} and {@code S3FileStorageService} against an
 * ephemeral MinIO server. The S3 client is the production adapter, not a test-only mock:
 * the assertions reach it through the HTTP endpoints used by the rest of the platform.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
class MinioStorageIntegrationTest extends AbstractMinioIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private Environment environment;

    @Value("${aws.s3.endpoint}")
    private String configuredEndpoint;

    @Value("${aws.s3.bucket}")
    private String configuredBucket;

    @Value("${file.storage.mode}")
    private String storageMode;

    @Test
    void createsBucketAutomatically() {
        assertTrue(s3Client.listBuckets().buckets().stream()
                .anyMatch(bucket -> BUCKET.equals(bucket.name())));
        assertEquals(BUCKET, configuredBucket);
        assertEquals(endpoint(), configuredEndpoint);
        assertTrue(environment.getProperty("aws.s3.path-style-access-enabled", Boolean.class));

        // The active bean is the real S3 adapter, not the local filesystem implementation.
        assertEquals("s3", storageMode);
        assertInstanceOf(S3FileStorageService.class, fileStorage);
    }

    @Test
    void uploadsObject() {
        byte[] content = "certificate payload".getBytes(StandardCharsets.UTF_8);

        String key = upload("certificate.pdf", content);

        assertTrue(key.endsWith("_certificate.pdf"));
        assertTrue(objectExists(key));
    }

    @Test
    void downloadsObject() {
        byte[] content = "downloaded certificate".getBytes(StandardCharsets.UTF_8);
        String key = upload("download.pdf", content);

        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/api/v1/files/download/{filename}", byte[].class, key);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(content, response.getBody());
    }

    @Test
    void deletesObject() {
        String key = upload("delete-me.pdf", "to be removed".getBytes(StandardCharsets.UTF_8));
        assertTrue(objectExists(key));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/files/{filename}", HttpMethod.DELETE, null, Void.class, key);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(objectExists(key));
    }

    @Test
    void missingObjectReturnsNotFound() {
        String key = "missing-" + UUID.randomUUID() + ".pdf";

        // The underlying S3 contract still answers 404 for HEAD and GET.
        S3Exception headError = assertThrows(S3Exception.class, () ->
                s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()));
        S3Exception getError = assertThrows(S3Exception.class, () -> getObject(key));
        assertEquals(404, headError.statusCode());
        assertEquals(404, getError.statusCode());

        // The controller maps the missing object to 404 for download and delete.
        ResponseEntity<byte[]> download = restTemplate.getForEntity(
                "/api/v1/files/download/{filename}", byte[].class, key);
        assertEquals(HttpStatus.NOT_FOUND, download.getStatusCode());

        ResponseEntity<Void> delete = restTemplate.exchange(
                "/api/v1/files/{filename}", HttpMethod.DELETE, null, Void.class, key);
        assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
    }

    /** Uploads through the real multipart endpoint and returns the generated storage key. */
    private String upload(String originalName, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return originalName;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/files/upload", new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return (String) response.getBody().get("filename");
    }
}
