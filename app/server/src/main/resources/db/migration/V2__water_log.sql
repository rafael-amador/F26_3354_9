-- Water log in liters. Team repo code (spec/04-team-repo-code/db/init.sql, LogWater.java):
--   CREATE TABLE WATER_LOG (id, userId -> USERS, amount FLOAT NOT NULL, loggedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
--   "Application supports both liters and gallons, but only liters will be entered into db."
-- Names follow this schema's snake_case convention and user_id is BIGINT to match users.id (A-34).
-- FLOAT(24) = single-precision FLOAT on both MySQL and H2.
CREATE TABLE water_log (
    id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT      NOT NULL,
    amount    FLOAT(24)   NOT NULL,                        -- liters, truncated to 2 decimals
    logged_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_water_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_water_log_user_time ON water_log(user_id, logged_at);

-- Carry any existing entries over (ml -> liters, truncated to 2 decimals like LogWater.enter).
INSERT INTO water_log (user_id, amount, logged_at)
    SELECT student_id, FLOOR(volume_ml / 10.0) / 100.0, logged_at FROM water_entries;

DROP TABLE water_entries;
