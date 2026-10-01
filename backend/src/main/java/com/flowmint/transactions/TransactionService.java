package com.flowmint.transactions;

import com.flowmint.accounts.Account;
import com.flowmint.accounts.AccountRepository;
import com.flowmint.common.ApiException;
import com.flowmint.common.AppTime;
import com.flowmint.common.PageResponse;
import com.flowmint.events.RawEvent;
import com.flowmint.events.RawEventRepository;
import com.flowmint.rules.MerchantRuleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Service
public class TransactionService {
    private static final BigDecimal MANUAL_CONFIDENCE = BigDecimal.ONE;
    private final TransactionRepository transactions;
    private final RawEventRepository rawEvents;
    private final AccountRepository accounts;
    private final MerchantRuleService merchantRules;
    private final AppTime time;

    public TransactionService(TransactionRepository transactions, RawEventRepository rawEvents, AccountRepository accounts, MerchantRuleService merchantRules, AppTime time) {
        this.transactions = transactions;
        this.rawEvents = rawEvents;
        this.accounts = accounts;
        this.merchantRules = merchantRules;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponses.Item> list(TransactionFilter filter, int page, int size, TransactionSort sort) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) throw ApiException.validation("from", "Must not be after to");
        return PageResponse.of(transactions.findAll(filter.toSpecification(), PageRequest.of(page, size, sort.sort())), TransactionResponses.Item::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponses.Detail get(UUID id) {
        return detail(find(id));
    }

    @Transactional
    public TransactionResponses.Detail update(UUID id, TransactionPatch patch) {
        Transaction transaction = find(id);
        patch.merchant().ifPresent(transaction::setMerchant);
        patch.amount().ifPresent(transaction::setAmount);
        patch.type().ifPresent(type -> {
            transaction.setType(type);
            if (type.impliedDirection() != null) transaction.setDirection(type.impliedDirection());
        });
        patch.direction().ifPresent(direction -> {
            if (transaction.getType().impliedDirection() == null) transaction.setDirection(direction);
        });
        patch.category().ifPresent(transaction::setCategory);
        if (patch.accountIdPresent()) transaction.setAccount(patch.accountId() == null ? null : account(patch.accountId()));
        patch.date().ifPresent(transaction::setTransactionDate);
        if (patch.notesPresent()) transaction.setNotes(patch.notes());
        if (patch.changesFields()) transaction.markUserEdited();
        if (patch.reviewed()) transaction.markReviewed();
        if (patch.applyToMerchant()) merchantRules.apply(transaction.getMerchant(), transaction.getCategory(), transaction.getId());
        return detail(transaction);
    }

    @Transactional
    public TransactionResponses.Detail create(ManualTransactionRequest request) {
        if (request.type() == TransactionType.UNKNOWN) throw ApiException.validation("type", "Choose a specific type");
        TransactionDirection direction = request.type().impliedDirection();
        if (direction == null) {
            if (request.direction() == null) throw ApiException.validation("direction", "Transfers need a direction");
            direction = request.direction();
        }
        Account account = request.accountId() == null ? null : account(request.accountId());
        Transaction transaction = new Transaction(null, "manual", ExtractionMethod.MANUAL, request.type(), direction, request.amount(), "INR",
            request.merchant().strip(), request.category(), PaymentChannel.OTHER, account, request.date(), time.noonOf(request.date()), MANUAL_CONFIDENCE, Set.of());
        String notes = request.notes() == null ? null : request.notes().strip();
        if (notes != null && !notes.isEmpty()) transaction.setNotes(notes);
        return detail(transactions.save(transaction));
    }

    @Transactional
    public void delete(UUID id) {
        Transaction transaction = find(id);
        transactions.clearDuplicatesOf(transaction.getId());
        transactions.delete(transaction);
    }

    @Transactional
    public TransactionResponses.Detail markNotDuplicate(UUID id) {
        Transaction transaction = find(id);
        transaction.clearDuplicate();
        return detail(transaction);
    }

    private Transaction find(UUID id) {
        return transactions.findById(id).orElseThrow(() -> ApiException.notFound("Transaction not found"));
    }

    private Account account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> ApiException.validation("accountId", "Unknown account"));
    }

    private TransactionResponses.Detail detail(Transaction transaction) {
        RawEvent event = transaction.getExternalEventId() == null ? null : rawEvents.findByExternalEventId(transaction.getExternalEventId()).orElse(null);
        return TransactionResponses.Detail.from(transaction, event);
    }
}
