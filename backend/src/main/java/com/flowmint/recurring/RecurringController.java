package com.flowmint.recurring;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recurring")
public class RecurringController {
    private final RecurringService recurring;
    public RecurringController(RecurringService recurring) { this.recurring = recurring; }

    @GetMapping
    public RecurringService.Overview list(@RequestParam(defaultValue = "false") boolean includeDismissed) {
        return recurring.overview(includeDismissed);
    }

    @PostMapping("/{merchantKey}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String merchantKey) {
        recurring.dismiss(merchantKey);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{merchantKey}/dismiss")
    public ResponseEntity<Void> restore(@PathVariable String merchantKey) {
        recurring.restore(merchantKey);
        return ResponseEntity.noContent().build();
    }
}
