-- TD-1 (3) "Remember this device for 30 days": a remembered device signs in with username + password only;
-- the 6-digit code is required again on a new device or after 30 days. Only a SHA-256 hash of the device token is
-- stored (NFR Security "all user data will be encrypted"; A-39).
CREATE TABLE trusted_devices (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_trusted_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_trusted_user ON trusted_devices(user_id);
