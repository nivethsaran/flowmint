package com.flowmint.events;

import com.flowmint.common.ApiException;
import com.flowmint.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService events;
    private final EventQueryService queries;

    public EventController(EventService events, EventQueryService queries) {
        this.events = events;
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<EventQueryService.Item> list(
        @RequestParam(required = false) List<ProcessingStatus> status,
        @RequestParam(required = false) MessageKind kind,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return queries.list(status, kind, q, page, size);
    }

    @GetMapping("/stats")
    public Map<ProcessingStatus, Long> stats() {
        return queries.stats();
    }

    @GetMapping("/{id}")
    public EventQueryService.Item get(@PathVariable UUID id) {
        return queries.get(id).orElseThrow(() -> ApiException.notFound("Event not found"));
    }

    @PostMapping
    public ResponseEntity<EventResponse> accept(@Valid @RequestBody EventRequest request) {
        RawEvent event = events.accept(request);
        return ResponseEntity.accepted().body(new EventResponse(true, event.getId()));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<Void> reprocess(@PathVariable UUID id) {
        events.reprocess(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Integer>> reprocessAll(@RequestParam EventService.ReprocessScope scope) {
        return ResponseEntity.accepted().body(Map.of("scheduled", events.reprocess(scope)));
    }
}
