package br.com.example.activation.persistence;

import br.com.example.activation.domain.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {
    Optional<ActivationRequest> findByExternalId(String externalId);

    Page<ActivationRequest> findByStatusAndCustomerId(ActivationStatus status, String customerId, Pageable pageable);

    Page<ActivationRequest> findByStatus(ActivationStatus status, Pageable pageable);

    Page<ActivationRequest> findByCustomerId(String customerId, Pageable pageable);
}
