package com.circleguard.form.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FormOutboxScheduler {
    private final FormOutboxDispatcher dispatcher;
    public FormOutboxScheduler(FormOutboxDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Scheduled(fixedDelayString = "${form.outbox.poll-ms:1000}")
    public void drain() {
        for (int i = 0; i < 10 && dispatcher.dispatchOne(); i++) {
            // Each call has its own transaction and row lock.
        }
    }
}
