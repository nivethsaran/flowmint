package com.flowmint.transactions;

import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionRepository transactions;
    public TransactionController(TransactionRepository transactions) { this.transactions = transactions; }
    @GetMapping
    public List<TransactionResponse> list(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        List<Transaction> result = from != null && to != null ? transactions.findByTransactionDateBetweenOrderByTransactionDateDesc(from, to) : transactions.findTop20ByOrderByTransactionDateDesc();
        return result.stream().map(TransactionResponse::from).toList();
    }
}
