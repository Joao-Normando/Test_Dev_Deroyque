package br.com.example.activation.controller;

import jakarta.validation.constraints.*;

public record CreateActivationRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String externalId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String customerId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String productCode) {
}
