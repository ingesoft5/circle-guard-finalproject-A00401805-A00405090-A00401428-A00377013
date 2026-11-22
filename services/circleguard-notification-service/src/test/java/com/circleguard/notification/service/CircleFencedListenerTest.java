/**
 * CG-DOC: fast unit tests for the circle.fenced Kafka listener. The mapper and the
 * reservation service are mocked so the payload branches are deterministic.
 */
package com.circleguard.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CircleFencedListenerTest {

    private ObjectMapper objectMapper;
    private RoomReservationService roomReservationService;
    private CircleFencedListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        roomReservationService = mock(RoomReservationService.class);
        listener = new CircleFencedListener(objectMapper, roomReservationService);
    }

    @Test
    void cancelsReservationWhenLocationIsPresent() throws Exception {
        String event = "{\"circleId\":\"circle-1\",\"locationId\":\"room-1\"}";
        when(objectMapper.readValue(eq(event), ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("circleId", "circle-1", "locationId", "room-1"));

        listener.handleCircleFenced(event);

        verify(roomReservationService).cancelReservation("circle-1", "room-1");
    }

    @Test
    void skipsCancellationWhenLocationIsMissing() throws Exception {
        String event = "{\"circleId\":\"circle-2\"}";
        when(objectMapper.readValue(eq(event), ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("circleId", "circle-2"));

        listener.handleCircleFenced(event);

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void skipsCancellationWhenLocationIsEmpty() throws Exception {
        String event = "{\"circleId\":\"circle-3\",\"locationId\":\"\"}";
        when(objectMapper.readValue(eq(event), ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("circleId", "circle-3", "locationId", ""));

        listener.handleCircleFenced(event);

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void malformedPayloadIsSwallowedWithoutCancelling() throws Exception {
        String event = "not json";
        when(objectMapper.readValue(eq(event), ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenThrow(new RuntimeException("bad json"));

        listener.handleCircleFenced(event);

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }
}
