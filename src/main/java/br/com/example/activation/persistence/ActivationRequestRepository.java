package br.com.example.activation.persistence;

import br.com.example.activation.domain.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {
    Optional<ActivationRequest> findByExternalId(String externalId);

    Optional<ActivationRequest> findByIdAndOwnerSubject(UUID id, String ownerSubject);

    Page<ActivationRequest> findByOwnerSubjectAndStatusAndCustomerId(
            String ownerSubject, ActivationStatus status, String customerId, Pageable pageable);

    Page<ActivationRequest> findByOwnerSubjectAndStatus(
            String ownerSubject, ActivationStatus status, Pageable pageable);

    Page<ActivationRequest> findByOwnerSubjectAndCustomerId(
            String ownerSubject, String customerId, Pageable pageable);

    Page<ActivationRequest> findByOwnerSubject(String ownerSubject, Pageable pageable);
}
