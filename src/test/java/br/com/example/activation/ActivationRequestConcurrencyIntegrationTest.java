package br.com.example.activation;

import br.com.example.activation.domain.ActivationRequest;
import br.com.example.activation.integration.ProviderActivationClient;
import br.com.example.activation.persistence.ActivationRequestRepository;
import br.com.example.activation.service.ActivationRequestService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ActivationRequestConcurrencyIntegrationTest {
    private static final int CONCURRENT_REQUESTS = 10;
    private static final String EXTERNAL_DATABASE_URL = System.getenv("TEST_POSTGRES_URL");
    private static final String EXTERNAL_DATABASE_USERNAME =
            System.getenv().getOrDefault("TEST_POSTGRES_USERNAME", "activation");
    private static final String EXTERNAL_DATABASE_PASSWORD =
            System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "activation");

    private static final PostgreSQLContainer<?> POSTGRES = startContainerIfNeeded();

    private static PostgreSQLContainer<?> startContainerIfNeeded() {
        if (EXTERNAL_DATABASE_URL != null && !EXTERNAL_DATABASE_URL.isBlank()) {
            return null;
        }
        var postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        return postgres;
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
            registry.add("spring.datasource.username", () -> EXTERNAL_DATABASE_USERNAME);
            registry.add("spring.datasource.password", () -> EXTERNAL_DATABASE_PASSWORD);
            return;
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopContainer() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private ActivationRequestRepository repository;

    @MockBean
    private ProviderActivationClient provider;

    @BeforeEach
    void resetProvider() {
        reset(provider);
    }

    @Test
    void concurrentRequestsWithSameExternalIdCreateOnlyOneActivation() throws Exception {
        String externalId = "EXT-CONCURRENT-" + java.util.UUID.randomUUID();
        var ready = new CountDownLatch(CONCURRENT_REQUESTS);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        try {
            List<Future<ActivationRequest>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent requests");
                    }
                    return service.create(externalId, "CUST-1", "NET-1", "concurrency-test");
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ActivationRequest> results = new ArrayList<>();
            for (Future<ActivationRequest> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            var storedRequest = repository.findByExternalId(externalId).orElseThrow();
            assertThat(results)
                    .extracting(ActivationRequest::getId)
                    .containsOnly(storedRequest.getId());
            verify(provider, times(1)).activate(externalId, "CUST-1", "NET-1");
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            repository.findByExternalId(externalId).ifPresent(repository::delete);
        }
    }
}
