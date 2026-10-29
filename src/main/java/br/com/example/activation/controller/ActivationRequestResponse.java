package br.com.example.activation.controller;

import br.com.example.activation.domain.*;

import java.time.Instant;
import java.util.UUID;

public record ActivationRequestResponse(UUID id, String externalId, String customerId, String productCode,
                                        ActivationStatus status, Instant createdAt, Instant updatedAt) {
    public static ActivationRequestResponse from(ActivationRequest r) {
        return new ActivationRequestResponse(r.getId(), r.getExternalId(), r.getCustomerId(), r.getProductCode(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
