package br.com.example.activation.service;

import br.com.example.activation.domain.ActivationRequest;
import br.com.example.activation.domain.ActivationStatus;
import br.com.example.activation.persistence.ActivationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestStateService {
    private final ActivationRequestRepository repository;

    public ActivationRequestStateService(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ActivationRequest changeStatus(UUID id, ActivationStatus status) {
        var request = findById(id);
        request.changeStatusTo(status);
        return repository.saveAndFlush(request);
    }

    private ActivationRequest findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }
}
