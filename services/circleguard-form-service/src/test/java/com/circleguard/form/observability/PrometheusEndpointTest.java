package com.circleguard.form.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PrometheusEndpointTest.Application.class, properties = "management.health.defaults.enabled=false")
@AutoConfigureMetrics
@AutoConfigureMockMvc(addFilters = false)
class PrometheusEndpointTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesPrometheusMetricsWithBoundedLabels() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("service=\"form\"")))
                .andExpect(content().string(containsString("environment=\"local\"")));
    }

    @Test
    void hidesHealthDetailsAndAdministrativeEndpoints() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().string(not(containsString("components"))));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import(FormObservabilityConfiguration.class)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class, KafkaAutoConfiguration.class})
    static class Application { }
}
