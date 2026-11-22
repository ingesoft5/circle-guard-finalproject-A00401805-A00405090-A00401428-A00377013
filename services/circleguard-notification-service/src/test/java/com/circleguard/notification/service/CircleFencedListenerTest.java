/**
 * CG-DOC: Unit tests for the circle.fenced listener routing and its defensive floor.
 */
package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CircleFencedListenerTest {

    @Mock
    private RoomReservationService roomReservationService;

    private CircleFencedListener listener;

    @BeforeEach
    void setUp() {
        listener = new CircleFencedListener(new ObjectMapper(), roomReservationService);
    }

    @Test
    void cancelsTheReservationWhenTheEventCarriesALocation() {
        listener.handleCircleFenced("{\"circleId\":\"circle-1\",\"locationId\":\"room-42\"}");

        verify(roomReservationService).cancelReservation("circle-1", "room-42");
    }

    @Test
    void skipsTheCancellationWhenTheLocationIsMissing() {
        listener.handleCircleFenced("{\"circleId\":\"circle-1\"}");

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void skipsTheCancellationWhenTheLocationIsEmpty() {
        listener.handleCircleFenced("{\"circleId\":\"circle-1\",\"locationId\":\"\"}");

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void swallowsAMalformedEventWithoutCancellingAnything() {
        assertThatCode(() -> listener.handleCircleFenced("not-a-json-payload"))
                .doesNotThrowAnyException();

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }
}
