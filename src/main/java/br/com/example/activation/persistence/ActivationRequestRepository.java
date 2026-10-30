package br.com.example.activation.persistence;

import br.com.example.activation.domain.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {
    Optional<ActivationRequest> findByExternalId(String externalId);

    Optional<ActivationRequest> findByIdAndOwnerSubject(UUID id, String ownerSubject);

    @Query("""
            select request from ActivationRequest request
            where request.ownerSubject = :ownerSubject
              and (:status is null or request.status = :status)
              and (:customerId is null or request.customerId = :customerId)
            """)
    Page<ActivationRequest> findByOwnerSubjectAndOptionalFilters(
            @Param("ownerSubject") String ownerSubject,
            @Param("status") ActivationStatus status,
            @Param("customerId") String customerId,
            Pageable pageable);
}
