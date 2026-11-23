/** CG-DOC: Repository for querying and updating persisted form outbox events. */
package com.circleguard.form.repository;

import com.circleguard.form.model.FormOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface FormOutboxRepository extends JpaRepository<FormOutboxEvent, UUID> {
    @Query(value = "SELECT * FROM form_event_outbox WHERE next_attempt_at <= CURRENT_TIMESTAMP "
            + "ORDER BY next_attempt_at, created_at LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<FormOutboxEvent> lockNextDue();
}
