package com.flowmint.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.UUID;

@Service
public class EventService {
    private final RawEventRepository rawEvents;
    private final ApplicationEventPublisher publisher;

    public EventService(RawEventRepository rawEvents, ApplicationEventPublisher publisher) { this.rawEvents = rawEvents; this.publisher = publisher; }

    @Transactional
    public RawEvent accept(EventRequest request) {
        String externalId = request.id() == null ? UUID.randomUUID().toString() : request.id().toString();
        return rawEvents.findByExternalEventId(externalId).orElseGet(() -> {
            RawEvent event = rawEvents.save(new RawEvent(UUID.randomUUID(), externalId, request.source(), request.sender(), request.packageName(), request.title(), request.body(), request.timestamp(), request.deviceId()));
            publisher.publishEvent(new RawEventAccepted(event.getId()));
            return event;
        });
    }
}
