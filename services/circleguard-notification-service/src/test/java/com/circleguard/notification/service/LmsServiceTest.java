/**
 * CG-DOC: pure unit test for the LMS adapter: the implementation returns a
 * completed future without external I/O, so no Spring context is required.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LmsServiceTest {

    private final LmsService lmsService = new LmsServiceImpl();

    @Test
    void testRemoteAttendanceSync() {
        CompletableFuture<Void> future = lmsService.syncRemoteAttendance("student-123", "PROBABLE");
        future.join();
        assertThat(future).isCompleted();
    }
}
