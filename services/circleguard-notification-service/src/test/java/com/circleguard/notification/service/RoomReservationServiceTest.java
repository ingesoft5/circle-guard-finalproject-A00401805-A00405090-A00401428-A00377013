/**
 * CG-DOC: pure unit test for the room-reservation adapter: the implementation
 * returns a completed future without external I/O, so no Spring context is needed.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RoomReservationServiceTest {

    private final RoomReservationService roomReservationService = new RoomReservationServiceImpl();

    @Test
    void testCancelReservation() {
        CompletableFuture<Void> future = roomReservationService.cancelReservation("circle-1", "loc-1");
        future.join();
        assertThat(future).isCompleted();
    }
}
