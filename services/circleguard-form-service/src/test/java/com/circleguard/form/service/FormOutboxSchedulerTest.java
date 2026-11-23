/** CG-DOC: Tests for Form Outbox Scheduler. */
package com.circleguard.form.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormOutboxSchedulerTest {
    @Test
    void drainsUntilDispatcherReportsEmpty() {
        FormOutboxDispatcher dispatcher = mock(FormOutboxDispatcher.class);
        when(dispatcher.dispatchOne()).thenReturn(true, true, false);

        new FormOutboxScheduler(dispatcher).drain();

        verify(dispatcher, times(3)).dispatchOne();
    }

    @Test
    void stopsAfterTenSuccessfulBatches() {
        FormOutboxDispatcher dispatcher = mock(FormOutboxDispatcher.class);
        when(dispatcher.dispatchOne()).thenReturn(true);

        new FormOutboxScheduler(dispatcher).drain();

        verify(dispatcher, times(10)).dispatchOne();
    }
}
