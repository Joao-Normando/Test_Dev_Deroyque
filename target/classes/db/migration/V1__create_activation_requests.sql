CREATE TABLE activation_requests (
 id UUID PRIMARY KEY, external_id VARCHAR(64) NOT NULL, customer_id VARCHAR(64) NOT NULL,
 product_code VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_activation_external_id UNIQUE(external_id),
 CONSTRAINT chk_activation_status CHECK(status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
);
CREATE INDEX idx_activation_customer ON activation_requests(customer_id);
CREATE INDEX idx_activation_status_created ON activation_requests(status,created_at);
