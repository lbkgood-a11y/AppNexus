package com.appnexus.poc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class Argon2PasswordBenchmarkTest {

    @Test
    void candidateParametersRoundTripWithinAReasonablePocGuardrail() {
        Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        Instant start = Instant.now();
        String encoded = encoder.encode("correct horse battery staple with a long passphrase");
        Duration encodeTime = Duration.between(start, Instant.now());

        assertThat(encoded).startsWith("$argon2id$");
        assertThat(encoder.matches("correct horse battery staple with a long passphrase", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
        assertThat(encodeTime).isLessThan(Duration.ofSeconds(2));
        System.out.println("POC_ARGON2_ENCODE_MILLIS=" + encodeTime.toMillis());
    }
}
