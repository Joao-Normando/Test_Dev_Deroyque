package br.com.example.activation.service;

import br.com.example.activation.domain.*;
import br.com.example.activation.integration.*;
import br.com.example.activation.persistence.ActivationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {
    private final ActivationRequestRepository repository;
    private final ProviderActivationClient providerClient;
    private final ActivationRequestStateService stateService;

    public ActivationRequestService(
            ActivationRequestRepository repository,
            ProviderActivationClient providerClient,
            ActivationRequestStateService stateService) {
        this.repository = repository;
        this.providerClient = providerClient;
        this.stateService = stateService;
    }

    public ActivationRequest create(
            String externalId, String customerId, String productCode, String ownerSubject) {
        var existingRequest = repository.findByExternalId(externalId);
        if (existingRequest.isPresent()) {
            return returnExistingRequestOrThrowConflict(
                    existingRequest.get(), customerId, productCode, ownerSubject);
        }

        ActivationRequest newRequest;
        try {
            newRequest = repository.saveAndFlush(
                    ActivationRequest.create(externalId, customerId, productCode, ownerSubject));
        } catch (DataIntegrityViolationException exception) {
            var concurrentRequest = repository.findByExternalId(externalId);
            if (concurrentRequest.isEmpty()) {
                throw exception;
            }
            return returnExistingRequestOrThrowConflict(
                    concurrentRequest.get(), customerId, productCode, ownerSubject);
        }

        return activateRequest(newRequest.getId());
    }

    private ActivationRequest activateRequest(UUID requestId) {
        var request = stateService.changeStatus(requestId, ActivationStatus.PROCESSING);
        try {
            providerClient.activate(
                    request.getExternalId(), request.getCustomerId(), request.getProductCode());
            return stateService.changeStatus(requestId, ActivationStatus.COMPLETED);
        } catch (ProviderActivationException e) {
            return stateService.changeStatus(requestId, ActivationStatus.FAILED);
        }
    }

    @Transactional(readOnly = true)
    public ActivationRequest get(UUID requestId, String ownerSubject) {
        return repository.findByIdAndOwnerSubject(requestId, ownerSubject)
                .orElseThrow(() -> new ActivationRequestNotFoundException(requestId));
    }

    @Transactional(readOnly = true)
    public Page<ActivationRequest> list(
            String ownerSubject, ActivationStatus status, String customerId, Pageable pageable) {
        return repository.findByOwnerSubjectAndOptionalFilters(
                ownerSubject, status, customerId, pageable);
    }

    private ActivationRequest returnExistingRequestOrThrowConflict(
            ActivationRequest existingRequest,
            String customerId,
            String productCode,
            String ownerSubject) {
        if (isSameRequest(existingRequest, customerId, productCode, ownerSubject)) {
            return existingRequest;
        }
        throw new DuplicateExternalIdException(
                "externalId já utilizado por uma solicitação incompatível.");
    }

    private boolean isSameRequest(
            ActivationRequest request, String customerId, String productCode, String ownerSubject) {
        return request.getOwnerSubject().equals(ownerSubject)
                && request.getCustomerId().equals(customerId)
                && request.getProductCode().equals(productCode);
    }

}
