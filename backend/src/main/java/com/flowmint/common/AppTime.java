package com.flowmint.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;

/** "Today" and "this month" in the operator's time zone, so month boundaries match their calendar. */
@Component
public class AppTime {
    private final Clock clock;
    private final ZoneId zone;

    public AppTime(Clock clock, @Value("${app.timezone:Asia/Kolkata}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public Instant now() { return clock.instant(); }
    public ZoneId zone() { return zone; }
    public LocalDate today() { return LocalDate.now(clock.withZone(zone)); }
    public YearMonth currentMonth() { return YearMonth.from(today()); }

    /** A representative instant for a date without a time (manual entries): noon local time. */
    public Instant noonOf(LocalDate date) { return date.atTime(LocalTime.NOON).atZone(zone).toInstant(); }
}
