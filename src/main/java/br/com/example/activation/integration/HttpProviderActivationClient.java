package br.com.example.activation.integration;

import org.springframework.beans.factory.annotation.Value;
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
                    .body(new Payload(externalId, customerId, productCode)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ProviderActivationException(
                    "Provider HTTP " + status, status == 429 || status == 503 || status >= 500);
        } catch (ResourceAccessException e) {
            throw new ProviderActivationException("Provider timeout or connection failure", true);
        }
    }

    private record Payload(String externalId, String customerId, String productCode) {
    }
}
