package com.circleguard.notification.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Export operational attributes only; exception messages can contain personal data. */
public final class PrivacySpanExporter implements SpanExporter {
    private static final Set<String> ALLOWED = Set.of("http.method", "http.status_code", "http.route",
            "method", "outcome", "messaging.system",
            "messaging.operation", "messaging.destination.name", "spring.kafka.template.name", "bean.name");
    private final SpanExporter delegate;

    public PrivacySpanExporter(SpanExporter delegate) { this.delegate = delegate; }

    @Override public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().map(span -> (SpanData) new DelegatingSpanData(span) {
            @Override public Attributes getAttributes() {
                return span.getAttributes().toBuilder().removeIf(key -> !ALLOWED.contains(key.getKey())).build();
            }
            @Override public List<EventData> getEvents() { return List.of(); }
            @Override public StatusData getStatus() {
                return StatusData.create(span.getStatus().getStatusCode(), "");
            }
        }).toList());
    }
    @Override public CompletableResultCode flush() { return delegate.flush(); }
    @Override public CompletableResultCode shutdown() { return delegate.shutdown(); }
}
