package com.flowmint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, BearerDeviceFilter bearer) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/info").permitAll().anyRequest().authenticated()).addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean BearerDeviceFilter bearer(@Value("${app.device-token}") String configuredToken) { return new BearerDeviceFilter(configuredToken); }
    static class BearerDeviceFilter extends OncePerRequestFilter {
        private final String configuredToken;
        BearerDeviceFilter(String configuredToken) { this.configuredToken = configuredToken; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ") && java.security.MessageDigest.isEqual(header.substring(7).getBytes(), configuredToken.getBytes())) {
                var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("device", null, java.util.List.of());
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(request, response);
        }
    }
}
