package com.flowmint.events;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RawEventRepository extends JpaRepository<RawEvent, UUID> {
    Optional<RawEvent> findByExternalEventId(String externalEventId);
}
