/**
 * CG-DOC: Base class that starts an ephemeral MinIO container for file-service integration tests.
 */
package com.circleguard.file.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Owns the MinIO lifecycle, the auto-created bucket and the S3 helpers shared by the
 * integration tests. The container is static so a single MinIO instance serves the class.
 *
 * <p>The image is pinned by version because the upstream {@code minio/minio} images are no
 * longer publicly available; the Bitnami legacy image exposes the same S3 API on port 9000.
 * No production code depends on this class or on the AWS SDK.</p>
 */
public abstract class AbstractMinioIntegrationTest {

    /** Pinned MinIO-compatible server image, pullable from Docker Hub. */
    protected static final String MINIO_IMAGE = "bitnamilegacy/minio:2025.4.22";
    /** Bucket created automatically before the first test. */
    protected static final String BUCKET = "circleguard-files";
    /** Test-only access key (never used outside the ephemeral container). */
    protected static final String ACCESS_KEY = "circleguard_test";
    /** Test-only secret key (never used outside the ephemeral container). */
    protected static final String SECRET_KEY = "circleguard_test_secret";
    /** Region used by the SDK and injected into the test profile. */
    protected static final Region REGION = Region.US_EAST_1;

    @Container
    protected static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                    .withExposedPorts(9000)
                    .withStartupTimeout(Duration.ofMinutes(3))
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    /** Client connected to the ephemeral endpoint; initialized after the container starts. */
    protected static S3Client s3Client;

    /**
     * Overrides the test-profile values with the container's host and random mapped port.
     * Dynamic properties have higher precedence than {@code application-test.yml}.
     */
    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.s3.endpoint", () -> endpoint());
        registry.add("aws.s3.access-key", () -> ACCESS_KEY);
        registry.add("aws.s3.secret-key", () -> SECRET_KEY);
        registry.add("aws.s3.bucket", () -> BUCKET);
        registry.add("aws.s3.region", REGION::id);
        registry.add("aws.s3.path-style-access-enabled", () -> true);
        // The whole Spring context must use the S3 adapter, not the local filesystem default.
        registry.add("file.storage.mode", () -> "s3");
    }

    /** Builds the S3 client and creates the bucket so no test depends on manual setup. */
    @BeforeAll
    static void startStorageContract() {
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(REGION)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException alreadyPresent) {
            // A restarted container may already own the bucket; the contract is the same.
        }
    }

    /** Removes objects written by the previous test so each case starts from an empty bucket. */
    @AfterEach
    void cleanBucket() {
        s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents()
                .forEach(object -> s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(object.key())
                        .build()));
    }

    /** Writes the container log as a build artifact and closes the SDK client. */
    @AfterAll
    static void dumpContainerLog() throws IOException {
        Path dir = Path.of("build", "integration-test");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("minio-container.log"), MINIO.getLogs());
        s3Client.close();
    }

    /** @return HTTP address of the ephemeral MinIO endpoint. */
    protected static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    /** Uploads an object to the test bucket. */
    protected static void putObject(String key, byte[] content) {
        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).build(),
                RequestBody.fromBytes(content));
    }

    /** Downloads an object from the test bucket. */
    protected static byte[] getObject(String key) {
        ResponseBytes<?> response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build());
        return response.asByteArray();
    }

    /** Deletes an object from the test bucket. */
    protected static void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(key).build());
    }

    /** @return true when the object exists, false when MinIO answers 404. */
    protected static boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }
}
