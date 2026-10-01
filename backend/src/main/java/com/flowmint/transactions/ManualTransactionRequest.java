package com.flowmint.transactions;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ManualTransactionRequest(
    @NotNull LocalDate date,
    @NotNull @Positive @DecimalMax("1000000000000") BigDecimal amount,
    @NotNull TransactionType type,
    @NotNull Category category,
    @NotBlank @Size(max = 200) String merchant,
    UUID accountId,
    TransactionDirection direction,
    @Size(max = 1000) String notes
) {}
