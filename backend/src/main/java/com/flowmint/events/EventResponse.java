package com.flowmint.events;

import java.util.UUID;

public record EventResponse(boolean accepted, UUID eventId) {}
