-- Lesson 13: allow FAILED for permanent early rejections (still no IN_PROGRESS).
ALTER TABLE idempotency_keys
    DROP CONSTRAINT IF EXISTS chk_idempotency_keys_status_completed;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT chk_idempotency_keys_status_completed_or_failed
    CHECK (status IN ('COMPLETED', 'FAILED'));
