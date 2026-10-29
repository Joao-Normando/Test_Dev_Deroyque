package br.com.example.activation.service;

import br.com.example.activation.domain.*;
import br.com.example.activation.integration.*;
import br.com.example.activation.persistence.ActivationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {
    private final ActivationRequestRepository repo;
    private final ProviderActivationClient provider;
    private final ActivationRequestStateService stateService;

    public ActivationRequestService(
            ActivationRequestRepository r,
            ProviderActivationClient p,
            ActivationRequestStateService stateService) {
        repo = r;
        provider = p;
        this.stateService = stateService;
    }

    public ActivationRequest create(
            String externalId, String customerId, String productCode, String ownerSubject) {
        var old = repo.findByExternalId(externalId);
        if (old.isPresent()) {
            return same(old.get(), customerId, productCode, ownerSubject) ? old.get() : conflict();
        }

        ActivationRequest saved;
        try {
            saved = repo.saveAndFlush(
                    ActivationRequest.create(externalId, customerId, productCode, ownerSubject));
        } catch (DataIntegrityViolationException e) {
            var concurrent = repo.findByExternalId(externalId);
            if (concurrent.isPresent()
                    && same(concurrent.get(), customerId, productCode, ownerSubject)) {
                return concurrent.get();
            }
            if (concurrent.isPresent()) {
                return conflict();
            }
            throw e;
        }

        return activate(saved.getId());
    }

    private ActivationRequest activate(UUID id) {
        var request = stateService.markProcessing(id);
        try {
            provider.activate(
                    request.getExternalId(), request.getCustomerId(), request.getProductCode());
            return stateService.markCompleted(id);
        } catch (ProviderActivationException e) {
            return stateService.markFailed(id);
        }
    }

    @Transactional(readOnly = true)
    public ActivationRequest get(UUID id, String ownerSubject) {
        return repo.findByIdAndOwnerSubject(id, ownerSubject)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<ActivationRequest> list(
            String ownerSubject, ActivationStatus status, String customerId, Pageable pageable) {
        if (status != null && customerId != null) {
            return repo.findByOwnerSubjectAndStatusAndCustomerId(
                    ownerSubject, status, customerId, pageable);
        }
        if (status != null) {
            return repo.findByOwnerSubjectAndStatus(ownerSubject, status, pageable);
        }
        if (customerId != null) {
            return repo.findByOwnerSubjectAndCustomerId(ownerSubject, customerId, pageable);
        }
        return repo.findByOwnerSubject(ownerSubject, pageable);
    }

    private boolean same(
            ActivationRequest request, String customerId, String productCode, String ownerSubject) {
        return request.getOwnerSubject().equals(ownerSubject)
                && request.getCustomerId().equals(customerId)
                && request.getProductCode().equals(productCode);
    }

    private ActivationRequest conflict() {
        throw new DuplicateExternalIdException(
                "externalId já utilizado por uma solicitação incompatível.");
    }
}
