/**
 * CG-DOC: Base class that starts an ephemeral Redis container for gateway integration tests.
 */
package com.circleguard.gateway.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public abstract class AbstractRedisIntegrationTest {

    protected static final String QR_SECRET = "my-super-secret-test-key-32-chars-long";
    protected static final String JWT_SECRET = "my-super-secret-jwt-key-32-chars-long-123";

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--save", "", "--appendonly", "no")
                    .withStartupTimeout(Duration.ofSeconds(60))
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("qr.secret", () -> QR_SECRET);
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    protected void seedStatus(String anonymousId, String status) {
        redisTemplate.opsForValue().set("user:status:" + anonymousId, status);
    }

    @AfterAll
    static void dumpContainerLog() throws IOException {
        Path dir = Path.of("build", "integration-test");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("redis-container.log"), REDIS.getLogs());
    }
}
