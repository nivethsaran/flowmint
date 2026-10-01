package com.flowmint.extraction;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @NotBlank String baseUrl,
    @NotBlank String apiKey,
    @NotBlank String model,
    @NotNull Duration timeout,
    @Positive int maxTokens,
    @Min(1) int maxAttempts,
    @NotEmpty List<Duration> retryDelays,
    @DecimalMin("0") @DecimalMax("1") double reviewConfidenceThreshold,
    @NotNull Duration claimTimeout,
    @Positive int queueCapacity
) {
    /** Delay before the next attempt after {@code attemptsSoFar} failed attempts. */
    public Duration retryDelay(int attemptsSoFar) {
        return retryDelays.get(Math.min(Math.max(attemptsSoFar - 1, 0), retryDelays.size() - 1));
    }
}
