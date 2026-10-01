package com.flowmint.extraction;

import com.flowmint.events.RawEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class TestData {
    static final Instant EVENT_TIME = Instant.parse("2026-10-01T09:00:00Z");

    private TestData() {}

    static LlmProperties properties() {
        return new LlmProperties("http://llm.test/v1", "key", "model", Duration.ofSeconds(5), 400, 5,
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60)), 0.7, Duration.ofMinutes(5), 10);
    }

    static RawEvent event(String body) {
        return event(null, body);
    }

    static RawEvent event(String title, String body) {
        return new RawEvent(UUID.randomUUID(), UUID.randomUUID().toString(), "sms", "VM-HDFCBK", null, title, body, EVENT_TIME, "pixel");
    }
}
