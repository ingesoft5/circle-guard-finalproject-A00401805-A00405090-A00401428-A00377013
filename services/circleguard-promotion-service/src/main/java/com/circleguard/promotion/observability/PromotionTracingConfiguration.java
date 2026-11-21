package com.circleguard.promotion.observability;

import io.micrometer.common.KeyValues;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.support.micrometer.KafkaRecordSenderContext;
import org.springframework.kafka.support.micrometer.KafkaTemplateObservation.DefaultKafkaTemplateObservationConvention;

@Configuration(proxyBeanMethods = false)
public class PromotionTracingConfiguration {
    @Bean
    @ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true")
    static BeanPostProcessor promotionKafkaObservation() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof KafkaTemplate<?, ?> template) {
                    configureTemplate(template);
                }
                return bean;
            }
        };
    }

    private static <K, V> void configureTemplate(KafkaTemplate<K, V> template) {
        template.setObservationEnabled(true);
        template.setObservationConvention(new DefaultKafkaTemplateObservationConvention() {
            @Override public KeyValues getHighCardinalityKeyValues(KafkaRecordSenderContext context) {
                return KeyValues.empty();
            }
        });
        // Spring Kafka's default producer listener logs failed records, including payloads.
        template.setProducerListener(new ProducerListener<>() { });
    }

    @Bean
    @ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true")
    SpanExporter promotionSpanExporter(@Value("${management.otlp.tracing.endpoint}") String endpoint) {
        return new PrivacySpanExporter(OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build());
    }
}
