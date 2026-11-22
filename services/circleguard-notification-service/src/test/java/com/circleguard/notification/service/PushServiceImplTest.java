/**
 * CG-DOC: fast unit tests for the push delivery implementation. The WebClient chain
 * is mocked so the HTTP branch is covered without a real Gotify endpoint.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushServiceImplTest {

    private WebClient webClient;
    private WebClient.RequestBodyUriSpec uriSpec;
    private WebClient.RequestBodySpec bodySpec;
    private WebClient.RequestHeadersSpec<?> headersSpec;
    private WebClient.ResponseSpec responseSpec;
    private AuditLogService auditLogService;
    private PushServiceImpl service;

    @BeforeEach
    void setUp() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        service = new PushServiceImpl(builder, "http://gotify.test");
        ReflectionTestUtils.setField(service, "webClient", webClient);
        auditLogService = mock(AuditLogService.class);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "gotifyToken", "MOCK_TOKEN");

        uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        bodySpec = mock(WebClient.RequestBodySpec.class);
        headersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        // bodyValue returns a wildcard-typed RequestHeadersSpec; doReturn avoids the
        // capture conversion that thenReturn cannot express here.
        doReturn(headersSpec).when(bodySpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void mockTokenShortCircuitsHttpCallWithMetadata() {
        service.sendAsync("student-1", "message", Map.of("client", "ios")).join();

        verify(auditLogService).logDelivery(eq("student-1"), eq("PUSH"), eq("SUCCESS"), anyString());
    }

    @Test
    void twoArgumentOverloadUsesTheSameMockPath() {
        service.sendAsync("student-2", "message").join();

        verify(auditLogService).logDelivery(eq("student-2"), eq("PUSH"), eq("SUCCESS"), anyString());
    }

    @Test
    void realTokenPostsPayloadWithExtrasAndAuditsSuccess() {
        ReflectionTestUtils.setField(service, "gotifyToken", "REAL_TOKEN");
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        service.sendAsync("student-3", "message", Map.of("client", "ios")).join();

        verify(uriSpec).uri("/message?token=REAL_TOKEN");
        verify(bodySpec).contentType(MediaType.APPLICATION_JSON);
        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).bodyValue(captured.capture());
        assertThat(captured.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captured.getValue();
        assertThat(payload).containsEntry("title", "CircleGuard Alert")
            .containsEntry("message", "message")
            .containsEntry("priority", 5)
            .containsEntry("extras", Map.of("client::notification", Map.of("client", "ios")));
        verify(auditLogService).logDelivery(eq("student-3"), eq("PUSH"), eq("SUCCESS"), anyString());
    }

    @Test
    void realTokenWithoutMetadataOmitsExtras() {
        ReflectionTestUtils.setField(service, "gotifyToken", "REAL_TOKEN");
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        service.sendAsync("student-4", "message", Map.of()).join();

        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).bodyValue(captured.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captured.getValue();
        assertThat(payload).doesNotContainKey("extras");
    }

    @Test
    void httpFailureAuditsRetryAndRethrows() {
        ReflectionTestUtils.setField(service, "gotifyToken", "REAL_TOKEN");
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new IllegalStateException("Gotify down")));

        assertThatThrownBy(() -> service.sendAsync("student-5", "message", Map.of()).join())
            .isInstanceOf(CompletionException.class);

        verify(auditLogService).logDelivery(eq("student-5"), eq("PUSH"), eq("RETRY"), anyString());
    }

    @Test
    void recoverAuditsFinalFailureAndReturnsFailedFuture() {
        RuntimeException failure = new RuntimeException("Gotify down");

        assertThatThrownBy(() -> service.recover(failure, "student-6", "message", Map.of()).join())
            .isInstanceOf(CompletionException.class)
            .hasCause(failure);

        verify(auditLogService).logDelivery(eq("student-6"), eq("PUSH"), eq("FAILED"), isNull());
    }
}
