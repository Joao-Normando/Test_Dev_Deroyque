package br.com.example.activation.integration;

public interface ProviderActivationClient {
    void activate(String externalId, String customerId, String productCode);
}
