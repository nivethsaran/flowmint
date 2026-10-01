package com.flowmint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.flowmint.auth.AuthProperties;
import com.flowmint.extraction.LlmProperties;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AuthProperties.class, LlmProperties.class})
public class FinancePlannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinancePlannerApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
