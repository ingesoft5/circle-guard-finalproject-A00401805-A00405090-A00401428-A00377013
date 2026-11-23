/**
 * CG-DOC: Unit or integration tests for Administrative Correction Test in the promotion service.
 */
package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public class AdministrativeCorrectionTest {

    // Jenkins shares an executor with other builds, so the default 60 s wait (retried
    // three times) can expire before a healthy container answers. One longer attempt
    // fails faster on a real hang and tolerates a slow cold start.
    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.12.0")
            .withAdminPassword("password")
            .withEnv("NEO4J_server_memory_heap_initial__size", "256m")
            .withEnv("NEO4J_server_memory_heap_max__size", "256m")
            .withEnv("NEO4J_server_memory_pagecache_size", "128m")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withStartupAttempts(1);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.1")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3))
            .withStartupAttempts(1);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withStartupAttempts(1);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // application-test.yml disables Flyway and Hibernate DDL, which is right for
        // unit tests but leaves the container database empty. Let the migrations build
        // the schema instead, so the real V1-V3 path is what these tests exercise.
        registry.add("spring.flyway.enabled", () -> "true");
        // This class starts no broker; without this the listener retries localhost:9092
        // for the whole run and floods the log.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private HealthStatusService statusService;

    @Autowired
    private CircleService circleService;

    @Autowired
    private UserNodeRepository userRepository;

    @Autowired
    private CircleNodeRepository circleRepository;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setup() {
        circleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void invalidateCircle_PreventsPropagation() {
        // 1. Setup: A -> Circle (Invalid) -> B
        UserNode a = UserNode.builder().anonymousId("A").status("ACTIVE").build();
        UserNode b = UserNode.builder().anonymousId("B").status("ACTIVE").build();
        userRepository.save(a);
        userRepository.save(b);

        CircleNode circle = circleService.createCircle("RiskGroup", "loc1");
        userRepository.recordEncounter("A", "B", System.currentTimeMillis(), "loc1");
        circleRepository.joinCircle("A", circle.getInviteCode());
        circleRepository.joinCircle("B", circle.getInviteCode());

        // Invalidate circle
        circleService.toggleCircleValidity(circle.getId());

        // 2. Action: Purge encounters to isolate circle test, then promote A
        userRepository.purgeStaleEncounters(System.currentTimeMillis() + 10000); 
        statusService.updateStatus("A", "CONFIRMED");

        // 3. Verify: B should NOT be affected through the invalid circle
        assertThat(userRepository.findById("B").get().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void forceFence_PromotesAllMembers() {
        // 1. Setup: A and B in Circle
        UserNode a = UserNode.builder().anonymousId("A").status("ACTIVE").build();
        UserNode b = UserNode.builder().anonymousId("B").status("ACTIVE").build();
        userRepository.save(a);
        userRepository.save(b);

        CircleNode circle = circleService.createCircle("Forced containment", "loc2");
        circleRepository.joinCircle("A", circle.getInviteCode());
        circleRepository.joinCircle("B", circle.getInviteCode());

        // 2. Action: Force fence
        circleService.forceFenceCircle(circle.getId());

        // 3. Verify: Both should be PROBABLE
        assertThat(userRepository.findById("A").get().getStatus()).isEqualTo("PROBABLE");
        assertThat(userRepository.findById("B").get().getStatus()).isEqualTo("PROBABLE");
    }
}
