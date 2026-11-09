/**
 * CG-DOC: Unit tests for the identity access event contract.
 */
package com.circleguard.identity.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IdentityAccessEventTest {

    @Test
    void builderPopulatesPayloadAndMetadata() {
        UUID anonymousId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        IdentityAccessEvent event = IdentityAccessEvent.builder()
                .eventId("event-1")
                .eventType("audit.identity.accessed")
                .timestamp(timestamp)
                .source("circleguard-identity-service")
                .payload(IdentityAccessEvent.IdentityAccessPayload.builder()
                        .anonymousId(anonymousId)
                        .requestingUser("staff@example.com")
                        .accessStatus("SUCCESS")
                        .build())
                .metadata(IdentityAccessEvent.IdentityAccessMetadata.builder()
                        .correlationId("correlation-1")
                        .version(1)
                        .build())
                .build();

        assertEquals("event-1", event.getEventId());
        assertEquals("audit.identity.accessed", event.getEventType());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals("circleguard-identity-service", event.getSource());
        assertNotNull(event.getPayload());
        assertEquals(anonymousId, event.getPayload().getAnonymousId());
        assertEquals("staff@example.com", event.getPayload().getRequestingUser());
        assertEquals("SUCCESS", event.getPayload().getAccessStatus());
        assertNotNull(event.getMetadata());
        assertEquals("correlation-1", event.getMetadata().getCorrelationId());
        assertEquals(1, event.getMetadata().getVersion());
        assertNotNull(event.toString());
    }
}
