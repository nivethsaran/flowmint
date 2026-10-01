package com.flowmint.events;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record EventRequest(
    UUID id,
    @NotBlank @Size(max = 30) String source,
    @Size(max = 160) String sender,
    @Size(max = 200) String packageName,
    @Size(max = 300) String title,
    @NotBlank @Size(max = 10000) String body,
    @NotNull Instant timestamp,
    @NotBlank @Size(max = 120) String deviceId
) {}
