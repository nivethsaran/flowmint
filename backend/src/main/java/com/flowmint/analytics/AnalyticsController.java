package com.flowmint.analytics;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/summary")
    public AnalyticsSummary summary(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return analytics.summary(from, to);
    }
}
