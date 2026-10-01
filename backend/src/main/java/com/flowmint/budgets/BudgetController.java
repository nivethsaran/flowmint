package com.flowmint.budgets;

import com.flowmint.common.ApiException;
import com.flowmint.common.AppTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService budgets;
    private final AppTime time;

    public BudgetController(BudgetService budgets, AppTime time) {
        this.budgets = budgets;
        this.time = time;
    }

    @GetMapping
    public BudgetMonth month(@RequestParam(required = false) String month) {
        if (month == null) return budgets.month(time.currentMonth());
        try {
            return budgets.month(YearMonth.parse(month));
        } catch (DateTimeParseException e) {
            throw ApiException.validation("month", "Must be YYYY-MM");
        }
    }

    @PutMapping("/{category}")
    public ResponseEntity<Void> put(@PathVariable String category, @Valid @RequestBody LimitRequest request) {
        budgets.put(category, request.amount());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{category}")
    public ResponseEntity<Void> delete(@PathVariable String category) {
        budgets.delete(category);
        return ResponseEntity.noContent().build();
    }

    public record LimitRequest(@NotNull BigDecimal amount) {}
}
