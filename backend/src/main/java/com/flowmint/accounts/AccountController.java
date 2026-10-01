package com.flowmint.accounts;

import com.flowmint.common.ApiException;
import com.flowmint.common.AppTime;
import com.flowmint.transactions.TransactionDirection;
import com.flowmint.transactions.TransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AppTime time;

    public AccountController(AccountRepository accounts, TransactionRepository transactions, AppTime time) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.time = time;
    }

    public record AccountItem(UUID id, AccountType type, String institution, String last4, String displayName, BigDecimal lastKnownBalance, Instant balanceAsOf,
                              long transactionCount, LocalDate lastTransactionDate, BigDecimal monthDebits, BigDecimal monthCredits) {}

    public record RenameRequest(@NotBlank @Size(max = 80) String displayName) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<AccountItem> list() {
        Map<UUID, Object[]> activity = new HashMap<>();
        for (Object[] row : transactions.accountActivity()) activity.put((UUID) row[0], row);
        YearMonth month = time.currentMonth();
        Map<UUID, BigDecimal> debits = new HashMap<>();
        Map<UUID, BigDecimal> credits = new HashMap<>();
        for (Object[] row : transactions.accountFlows(month.atDay(1), month.atEndOfMonth())) {
            (row[1] == TransactionDirection.DEBIT ? debits : credits).put((UUID) row[0], (BigDecimal) row[2]);
        }
        return accounts.findAll().stream()
            .map(a -> {
                Object[] stats = activity.get(a.getId());
                return new AccountItem(a.getId(), a.getType(), a.getInstitution(), a.getLast4(), a.getDisplayName(), a.getLastKnownBalance(), a.getBalanceAsOf(),
                    stats == null ? 0 : (Long) stats[1], stats == null ? null : (LocalDate) stats[2],
                    debits.getOrDefault(a.getId(), BigDecimal.ZERO), credits.getOrDefault(a.getId(), BigDecimal.ZERO));
            })
            .sorted(Comparator.comparing(AccountItem::transactionCount).reversed().thenComparing(AccountItem::displayName))
            .toList();
    }

    @PatchMapping("/{id}")
    @Transactional
    public Map<String, Object> rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        Account account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Account not found"));
        String name = request.displayName().strip();
        if (name.isEmpty()) throw ApiException.validation("displayName", "Must not be blank");
        account.rename(name);
        return Map.of("id", account.getId(), "displayName", account.getDisplayName());
    }
}
