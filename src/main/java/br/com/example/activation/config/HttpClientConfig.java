package br.com.example.activation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClient.Builder restClientBuilder(
            @Value("${provider.connect-timeout:2s}") Duration connect,
            @Value("${provider.read-timeout:5s}") Duration read,
            ObservationRegistry observationRegistry) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connect);
        f.setReadTimeout(read);
        return RestClient.builder()
                .requestFactory(f)
                .observationRegistry(observationRegistry);
    }
}
