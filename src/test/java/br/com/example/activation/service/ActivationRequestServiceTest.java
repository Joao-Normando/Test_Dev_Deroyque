package br.com.example.activation.service;

import br.com.example.activation.api.CreateActivationRequest;
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
        when(repo.findByExternalId("E")).thenReturn(Optional.of(ActivationRequest.create("E", "C", "P")));
        var service = new ActivationRequestService(repo, provider);
        assertThatThrownBy(() -> service.create(new CreateActivationRequest("E", "C", "OTHER"))).isInstanceOf(DuplicateExternalIdException.class);
        verifyNoInteractions(provider);
    }
}
