package br.com.example.activation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter()))).build();
    }

    @Bean
    JwtAuthenticationConverter jwtConverter() {
        var s = new JwtGrantedAuthoritiesConverter();
        s.setAuthoritiesClaimName("scope");
        s.setAuthorityPrefix("SCOPE_");
        var c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(s);
        return c;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret, @Value("${security.jwt.issuer}") String issuer, @Value("${security.jwt.audience}") String audience) {
        var d = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        var issuerV = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> aud = jwt -> jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        d.setJwtValidator(jwt -> {
            var v = issuerV.validate(jwt);
            return v.hasErrors() ? v : aud.validate(jwt);
        });
        return d;
    }
}
