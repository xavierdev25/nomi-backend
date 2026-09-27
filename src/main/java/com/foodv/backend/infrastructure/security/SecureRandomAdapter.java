package com.foodv.backend.infrastructure.security;

import com.foodv.backend.domain.port.out.SecureRandomPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Implementación de {@code SecureRandomPort} con {@link java.security.SecureRandom}.
 */
@Component
public class SecureRandomAdapter implements SecureRandomPort {

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateConfirmationCode(int length) {
        if (length <= 0 || length > 12) length = 4;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public String generateRandomString(int length) {
        if (length <= 0 || length > 64) length = 16;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(secureRandom.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
