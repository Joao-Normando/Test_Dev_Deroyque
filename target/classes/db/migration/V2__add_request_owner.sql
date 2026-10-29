ALTER TABLE activation_requests
    ADD COLUMN owner_subject VARCHAR(255) NOT NULL DEFAULT 'legacy';

ALTER TABLE activation_requests
    ALTER COLUMN owner_subject DROP DEFAULT;

CREATE INDEX idx_activation_owner_customer
    ON activation_requests (owner_subject, customer_id);

CREATE INDEX idx_activation_owner_status_created
    ON activation_requests (owner_subject, status, created_at);
