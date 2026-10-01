package com.flowmint.common;

import org.junit.jupiter.api.Test;
import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class CommonTest {
    @Test
    void merchantKeyIgnoresCaseSpacingAndPunctuation() {
        assertThat(MerchantKey.of("AMAZON PAY INDIA")).isEqualTo(MerchantKey.of("Amazon Pay-India."));
        assertThat(MerchantKey.of("Swiggy")).isEqualTo("swiggy");
        assertThat(MerchantKey.of(null)).isEmpty();
    }

    @Test
    void todayFollowsTheConfiguredZone() {
        AppTime time = new AppTime(Clock.fixed(Instant.parse("2026-09-30T20:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata");

        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(time.currentMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(time.noonOf(LocalDate.of(2026, 10, 1))).isEqualTo(Instant.parse("2026-10-01T06:30:00Z"));
    }
}
