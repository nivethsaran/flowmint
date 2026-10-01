package com.flowmint.accounts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;

@Service
public class AccountService {
    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) { this.accounts = accounts; }

    /** Finds or creates the account identified by type and last four digits; null when either is unknown. */
    @Transactional
    public Account resolve(AccountType type, String last4, String institution) {
        if (type == null || type == AccountType.UNKNOWN || last4 == null) return null;
        Account account = accounts.findByTypeAndLast4(type, last4).orElseGet(() -> accounts.save(new Account(type, last4, institution)));
        account.fillInstitution(institution);
        return account;
    }

    @Transactional
    public void recordBalance(Account account, BigDecimal balance, Instant asOf) {
        if (account == null || balance == null || account.getType() == AccountType.CREDIT_CARD) return;
        account.recordBalance(balance, asOf);
    }
}
