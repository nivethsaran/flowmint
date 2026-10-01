package com.flowmint.transactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowmint.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionService transactions;
    public TransactionController(TransactionService transactions) { this.transactions = transactions; }

    @GetMapping
    public PageResponse<TransactionResponses.Item> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) Category category,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) TransactionDirection direction,
        @RequestParam(required = false) Boolean review,
        @RequestParam(defaultValue = "true") boolean includeDuplicates,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "date_desc") TransactionSort sort
    ) {
        return transactions.list(new TransactionFilter(q, type, category, accountId, direction, review, includeDuplicates, from, to), page, size, sort);
    }

    @GetMapping("/{id}")
    public TransactionResponses.Detail get(@PathVariable UUID id) {
        return transactions.get(id);
    }

    @PatchMapping("/{id}")
    public TransactionResponses.Detail update(@PathVariable UUID id, @RequestBody JsonNode body) {
        return transactions.update(id, TransactionPatch.parse(body));
    }

    @PostMapping
    public ResponseEntity<TransactionResponses.Detail> create(@Valid @RequestBody ManualTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactions.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        transactions.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/not-duplicate")
    public TransactionResponses.Detail notDuplicate(@PathVariable UUID id) {
        return transactions.markNotDuplicate(id);
    }
}
