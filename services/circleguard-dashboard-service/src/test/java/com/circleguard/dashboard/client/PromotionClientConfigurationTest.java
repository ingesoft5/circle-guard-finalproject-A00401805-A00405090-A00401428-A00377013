package com.circleguard.dashboard.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the promotion-service URL comes from external configuration
 * instead of being hardcoded in the client (E2.4 / External Configuration).
 */
class PromotionClientConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PromotionClient.class);

    @Test
    void shouldInjectPromotionUrlFromExternalizedProperty() {
        contextRunner
                .withPropertyValues("circleguard.promotion-service.url=http://promotion.test:8088")
                .run(context -> {
                    PromotionClient client = context.getBean(PromotionClient.class);
                    assertThat(ReflectionTestUtils.getField(client, "promotionServiceUrl"))
                            .isEqualTo("http://promotion.test:8088");
                });
    }
}
