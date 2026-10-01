package com.flowmint.events;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService events;
    public EventController(EventService events) { this.events = events; }
    @PostMapping
    public ResponseEntity<EventResponse> accept(@Valid @RequestBody EventRequest request) {
        RawEvent event = events.accept(request);
        return ResponseEntity.accepted().body(new EventResponse(true, event.getId()));
    }
}
