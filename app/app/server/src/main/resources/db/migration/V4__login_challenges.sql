-- TD-5 (2): "The password step alone must never create a full session (use a short-lived, single-use challenge that
-- only the code can complete)". TD-5 (3): after Register, the first 6-digit code finishes two-step setup.
-- Only a SHA-256 hash of the challenge id is stored (same rule as trusted_devices, A-39). Lifetime/attempts: A-42.
CREATE TABLE login_challenges (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    purpose     VARCHAR(16) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_challenge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_challenge_user ON login_challenges(user_id);
