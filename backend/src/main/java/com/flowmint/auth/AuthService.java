package com.flowmint.auth;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {
    private final AuthProperties properties;
    private final GoogleAuthenticator authenticator = new GoogleAuthenticator(new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().setWindowSize(1).build());

    public AuthService(AuthProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String username, String password, int totp) {
        return constantTimeEquals(username, properties.username())
            && constantTimeEquals(password, properties.password())
            && authenticator.authorize(properties.totpSecret(), totp);
    }

    public Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(properties.username(), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    private boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
