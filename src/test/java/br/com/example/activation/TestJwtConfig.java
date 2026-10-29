package br.com.example.activation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@TestConfiguration
public class TestJwtConfig {
    @Bean
    JwtEncoder jwtEncoder() {
        byte[] secret = "test-secret-key-that-is-at-least-32-bytes-long".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    }
}
