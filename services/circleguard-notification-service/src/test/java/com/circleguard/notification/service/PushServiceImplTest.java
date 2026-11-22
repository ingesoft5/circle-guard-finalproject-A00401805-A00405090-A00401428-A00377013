/**
 * CG-DOC: Unit tests for the push channel: mock-mode delivery, Gotify payload, retry audit and recovery.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PushServiceImplTest {

    @Mock
    private AuditLogService auditLogService;

    private PushServiceImpl pushService(String token, WebClient.Builder builder) {
        PushServiceImpl service = new PushServiceImpl(builder, "http://gotify.local");
        ReflectionTestUtils.setField(service, "gotifyToken", token);
        ReflectionTestUtils.setField(service, "gotifyUrl", "http://gotify.local");
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        return service;
    }

    @Test
    void mockTokenShortCircuitsDeliveryAndAuditsSuccess() {
        PushServiceImpl service = pushService("MOCK_TOKEN", WebClient.builder());

        CompletableFuture<Void> result = service.sendAsync("user-123", "payload", Map.of("url", "circleguard://guidelines"));

        assertThat(result).isCompleted();
        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logDelivery(eq("user-123"), eq("PUSH"), eq("SUCCESS"), correlationId.capture());
        assertThat(correlationId.getValue()).isNotBlank();
    }

    @Test
    void twoArgumentOverloadDelegatesWithEmptyMetadata() {
        PushServiceImpl service = pushService("MOCK_TOKEN", WebClient.builder());

        CompletableFuture<Void> result = service.sendAsync("user-123", "payload");

        assertThat(result).isCompleted();
        verify(auditLogService).logDelivery(eq("user-123"), eq("PUSH"), eq("SUCCESS"), any());
    }

    @Test
    void realTokenPostsTheGotifyPayloadAndAuditsSuccess() {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));
        PushServiceImpl service = pushService("REAL_TOKEN", builder);

        CompletableFuture<Void> result = service.sendAsync("user-123", "payload", Map.of("url", "circleguard://guidelines"));

        assertThat(result).isCompleted();
        verify(auditLogService).logDelivery(eq("user-123"), eq("PUSH"), eq("SUCCESS"), any());
    }

    @Test
    void transportFailureAuditsRetryAndRethrowsForTheRetryInterceptor() {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new RuntimeException("gotify down")));
        PushServiceImpl service = pushService("REAL_TOKEN", builder);

        assertThatThrownBy(() -> service.sendAsync("user-123", "payload", Map.of()))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logDelivery(eq("user-123"), eq("PUSH"), eq("RETRY"), correlationId.capture());
        assertThat(correlationId.getValue()).isNotBlank();
        verifyNoMoreInteractions(auditLogService);
    }

    @Test
    void recoveryAuditsFailedWithoutCorrelationIdAndReturnsFailedFuture() {
        PushServiceImpl service = pushService("MOCK_TOKEN", WebClient.builder());
        RuntimeException failure = new RuntimeException("gotify down");

        CompletableFuture<Void> recovered = service.recover(failure, "user-123", "payload", Map.of());

        assertThat(recovered).isCompletedExceptionally();
        assertThatThrownBy(recovered::join).hasCause(failure);
        verify(auditLogService).logDelivery("user-123", "PUSH", "FAILED", null);
        verifyNoMoreInteractions(auditLogService);
    }
}
