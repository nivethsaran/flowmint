package com.flowmint.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String totpSecret
) {}
