-- Lesson 11+: only COMPLETED is a valid key status.
ALTER TABLE idempotency_keys
    ADD CONSTRAINT chk_idempotency_keys_status_completed
    CHECK (status = 'COMPLETED');
