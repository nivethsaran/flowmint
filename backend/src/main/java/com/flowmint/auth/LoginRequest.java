package com.flowmint.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank @Pattern(regexp = "\\d{6}") String totp
) {}
