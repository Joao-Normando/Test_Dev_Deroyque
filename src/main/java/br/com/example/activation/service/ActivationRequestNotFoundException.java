package br.com.example.activation.service;

import java.util.UUID;

public class ActivationRequestNotFoundException extends RuntimeException {
    public ActivationRequestNotFoundException(UUID id) {
        super("Not found: " + id);
    }
}
