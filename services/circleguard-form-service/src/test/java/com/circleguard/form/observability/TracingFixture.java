/** CG-DOC: Test fixture for distributed tracing assertions. */
package com.circleguard.form.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/** In-memory telemetry only: no external collector or global SDK registration. */
public final class TracingFixture implements AutoCloseable {
    public final InMemorySpanExporter exported = InMemorySpanExporter.create();
    public final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(new PrivacySpanExporter(exported))).build();
    public final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build();
    public final OtelTracer tracer = new OtelTracer(sdk.getTracer("form-test"),
            new OtelCurrentTraceContext(), event -> { });
    public final ObservationRegistry observations = ObservationRegistry.create();

    public TracingFixture() {
        observations.observationConfig().observationHandler(
                new io.micrometer.observation.ObservationHandler.FirstMatchingCompositeObservationHandler(
                        new PropagatingSenderTracingObservationHandler<>(tracer,
                                new OtelPropagator(sdk.getPropagators(), sdk.getTracer("form-test"))),
                        new DefaultTracingObservationHandler(tracer)));
    }
    @Override public void close() { sdk.close(); }
}
