package com.flowmint.events;

import jakarta.validation.constraints.NotNull;

public record EventFeedbackRequest(@NotNull EventFeedback feedback) {}
