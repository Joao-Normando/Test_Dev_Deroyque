package br.com.example.activation.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class HttpProviderActivationClient implements ProviderActivationClient {
    private final RestClient client;

    public HttpProviderActivationClient(RestClient.Builder builder, @Value("${provider.base-url}") String baseUrl) {
        client = builder.baseUrl(baseUrl).build();
    }

    public void activate(String externalId, String customerId, String productCode) {
        try {
            client.post().uri("/provider/v1/activate").header("Idempotency-Key", externalId)
                    .body(new Payload(externalId, customerId, productCode)).retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ProviderStatusException(res.getStatusCode().value());
                    }).toBodilessEntity();
        } catch (ProviderStatusException e) {
            throw new ProviderActivationException("Provider HTTP " + e.status, e.status == 429 || e.status == 503 || e.status >= 500);
        } catch (RestClientResponseException e) {
            int value = e.getStatusCode().value();
            throw new ProviderActivationException("Provider HTTP " + value, value == 429 || value == 503 || value >= 500);
        } catch (ResourceAccessException e) {
            throw new ProviderActivationException("Provider timeout or connection failure", true);
        }
    }

    private record Payload(String externalId, String customerId, String productCode) {
    }

    private static class ProviderStatusException extends RuntimeException {
        final int status;

        ProviderStatusException(int value) {
            status = value;
        }
    }
}
