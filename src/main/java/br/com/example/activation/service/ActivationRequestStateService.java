package br.com.example.activation.service;

import br.com.example.activation.domain.ActivationRequest;
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
    public ActivationRequest markProcessing(UUID id) {
        var request = findById(id);
        request.markProcessing();
        return repository.saveAndFlush(request);
    }

    @Transactional
    public ActivationRequest markCompleted(UUID id) {
        var request = findById(id);
        request.markCompleted();
        return repository.saveAndFlush(request);
    }

    @Transactional
    public ActivationRequest markFailed(UUID id) {
        var request = findById(id);
        request.markFailed();
        return repository.saveAndFlush(request);
    }

    private ActivationRequest findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }
}
