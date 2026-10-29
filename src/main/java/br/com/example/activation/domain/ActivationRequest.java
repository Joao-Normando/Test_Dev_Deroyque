package br.com.example.activation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="activation_requests", uniqueConstraints=@UniqueConstraint(name="uk_activation_external_id", columnNames="external_id"),
 indexes={@Index(name="idx_activation_customer", columnList="customer_id"), @Index(name="idx_activation_status_created", columnList="status, created_at")})
public class ActivationRequest {
 @Id private UUID id;
 @Column(name="external_id", nullable=false, updatable=false, length=64) private String externalId;
 @Column(name="customer_id", nullable=false, updatable=false, length=64) private String customerId;
 @Column(name="product_code", nullable=false, updatable=false, length=64) private String productCode;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private ActivationStatus status;
 @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
 @Column(name="updated_at", nullable=false) private Instant updatedAt;
 @Version private long version;
 protected ActivationRequest() {}
 private ActivationRequest(String e, String c, String p) {
  id=UUID.randomUUID(); externalId=e; customerId=c; productCode=p; status=ActivationStatus.PENDING;
  createdAt=Instant.now(); updatedAt=createdAt;
 }
 public static ActivationRequest create(String e,String c,String p) { return new ActivationRequest(e,c,p); }
 public void markProcessing() { require(ActivationStatus.PENDING); status=ActivationStatus.PROCESSING; touch(); }
 public void markCompleted() { require(ActivationStatus.PROCESSING); status=ActivationStatus.COMPLETED; touch(); }
 public void markFailed() { require(ActivationStatus.PROCESSING); status=ActivationStatus.FAILED; touch(); }
 private void require(ActivationStatus expected) { if(status!=expected) throw new IllegalStateException("Invalid activation status transition"); }
 private void touch() { updatedAt=Instant.now(); }
 public UUID getId(){return id;} public String getExternalId(){return externalId;} public String getCustomerId(){return customerId;}
 public String getProductCode(){return productCode;} public ActivationStatus getStatus(){return status;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
