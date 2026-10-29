package br.com.example.activation.service;

import br.com.example.activation.domain.ActivationRequest;
import br.com.example.activation.integration.ProviderActivationClient;
import br.com.example.activation.persistence.ActivationRequestRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivationRequestServiceTest {
    @Test
    void reusedExternalIdWithDifferentPayloadConflicts() {
        var repo = mock(ActivationRequestRepository.class);
        var provider = mock(ProviderActivationClient.class);
        var stateService = mock(ActivationRequestStateService.class);
        when(repo.findByExternalId("E"))
                .thenReturn(Optional.of(ActivationRequest.create("E", "C", "P", "owner")));
        var service = new ActivationRequestService(repo, provider, stateService);
        assertThatThrownBy(() -> service.create("E", "C", "OTHER", "owner"))
                .isInstanceOf(DuplicateExternalIdException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void doesNotReturnAnotherConsumersRequestForAnIdempotentRetry() {
        var repo = mock(ActivationRequestRepository.class);
        var provider = mock(ProviderActivationClient.class);
        var stateService = mock(ActivationRequestStateService.class);
        when(repo.findByExternalId("E"))
                .thenReturn(Optional.of(ActivationRequest.create("E", "C", "P", "owner-a")));
        var service = new ActivationRequestService(repo, provider, stateService);

        assertThatThrownBy(() -> service.create("E", "C", "P", "owner-b"))
                .isInstanceOf(DuplicateExternalIdException.class);
        verifyNoInteractions(provider, stateService);
    }
}
