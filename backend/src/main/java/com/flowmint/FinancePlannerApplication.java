package com.flowmint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.flowmint.auth.AuthProperties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AuthProperties.class)
public class FinancePlannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinancePlannerApplication.class, args);
    }
}
