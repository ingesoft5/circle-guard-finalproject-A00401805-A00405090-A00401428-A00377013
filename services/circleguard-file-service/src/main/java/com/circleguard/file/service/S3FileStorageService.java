/**
 * CG-DOC: S3/MinIO storage adapter for the file service; active when file.storage.mode=s3 (HU-FILE-04).
 */
package com.circleguard.file.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;

/**
 * Real S3-compatible implementation backed by the AWS SDK v2. It works against AWS S3 and
 * against MinIO when path-style addressing and a custom endpoint are configured.
 */
@Service
@ConditionalOnProperty(name = "file.storage.mode", havingValue = "s3")
public class S3FileStorageService implements FileStorage {
    private final S3Client s3Client;
    private final String bucket;

    @Autowired
    public S3FileStorageService(@Value("${aws.s3.endpoint}") String endpoint,
                                @Value("${aws.s3.access-key}") String accessKey,
                                @Value("${aws.s3.secret-key}") String secretKey,
                                @Value("${aws.s3.bucket}") String bucket,
                                @Value("${aws.s3.region:us-east-1}") String region,
                                @Value("${aws.s3.path-style-access-enabled:true}") boolean pathStyleAccessEnabled) {
        this(S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccessEnabled)
                        .build())
                .build(), bucket);
    }

    /** Visible for unit tests, so the storage logic can be exercised without a running server. */
    S3FileStorageService(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String saveFile(MultipartFile file) {
        String key = FileStorage.generateFilename(file.getOriginalFilename());
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new RuntimeException("Could not read uploaded file", e);
        }
        return key;
    }

    @Override
    public Resource loadFile(String filename) {
        if (!FileStorage.isValidFilename(filename)) {
            return null;
        }
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(filename).build());
            return new ByteArrayResource(response.asByteArray()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public boolean deleteFile(String filename) {
        if (!FileStorage.isValidFilename(filename) || !objectExists(filename)) {
            return false;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(filename).build());
        return true;
    }

    /** @return true when the object exists; a 404 from S3/MinIO means it does not. */
    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** Releases SDK resources when the Spring context is closed. */
    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
