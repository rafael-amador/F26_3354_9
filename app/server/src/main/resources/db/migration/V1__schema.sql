-- CometFit schema. Every table maps to a class of the Deliverable 1 / Deliverable 2 draft class diagram
-- (D1 §5.2 = D2 §2.5.2, identical).
-- Database product: MySQL 8.4 (team repo code README "MySQL for database operations", db/docker-compose.yml
-- `mysql:8.4`; supersedes A-2). Also runs on H2 in MySQL mode for the automated tests.
-- Foreign keys are table constraints because MySQL ignores inline column REFERENCES.
-- Timestamps are DATETIME(6), stored in UTC (hibernate.jdbc.time_zone=UTC).

-- Users and Security package: User (abstract) <- Student, Developer
CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_type       VARCHAR(16)  NOT NULL,           -- STUDENT | DEVELOPER
    username        VARCHAR(40)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    email_enc       VARCHAR(512) NOT NULL,           -- AES-GCM encrypted (NFR Security)
    totp_secret_enc VARCHAR(512) NOT NULL,           -- AES-GCM encrypted (NFR Security)
    display_name    VARCHAR(80)  NOT NULL,
    is_public       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Student
    calorie_goal    INTEGER,
    water_goal_ml   INTEGER,
    step_goal       INTEGER,
    bottle_size_ml  INTEGER,                         -- A-8
    -- Developer
    admin_role      VARCHAR(40),
    created_at      DATETIME(6)  NOT NULL
);

CREATE TABLE developer_permissions (
    user_id    BIGINT      NOT NULL,
    permission VARCHAR(60) NOT NULL,
    PRIMARY KEY (user_id, permission),
    CONSTRAINT fk_devperm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE sessions (
    token      VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Domain Data package
CREATE TABLE water_entries (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT      NOT NULL,
    volume_ml  INTEGER     NOT NULL,
    logged_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_water_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_water_student_time ON water_entries(student_id, logged_at);

CREATE TABLE dining_items (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    external_id VARCHAR(60)  NOT NULL UNIQUE,        -- id in the UTD Dining feed
    name        VARCHAR(120) NOT NULL,
    location    VARCHAR(80)  NOT NULL,
    calories    INTEGER      NOT NULL,
    protein_g   DOUBLE PRECISION NOT NULL,
    carbs_g     DOUBLE PRECISION NOT NULL,
    fat_g       DOUBLE PRECISION NOT NULL
);

CREATE TABLE meal_entries (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id     BIGINT       NOT NULL,
    food_name      VARCHAR(120) NOT NULL,
    calories       INTEGER      NOT NULL,
    protein_g      DOUBLE PRECISION NOT NULL,
    carbs_g        DOUBLE PRECISION NOT NULL,
    fat_g          DOUBLE PRECISION NOT NULL,
    source         VARCHAR(16)  NOT NULL,            -- MealSource: MANUAL | PHOTO | DINING_MENU
    dining_item_id BIGINT,
    logged_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_meal_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_dining FOREIGN KEY (dining_item_id) REFERENCES dining_items(id)
);
CREATE INDEX ix_meal_student_time ON meal_entries(student_id, logged_at);

CREATE TABLE smart_devices (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id   VARCHAR(80) NOT NULL UNIQUE,
    device_type VARCHAR(40) NOT NULL,
    platform    VARCHAR(40) NOT NULL
);

CREATE TABLE device_links (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT      NOT NULL,
    smart_device_id BIGINT      NOT NULL,
    linked_at       DATETIME(6) NOT NULL,
    status          VARCHAR(16) NOT NULL,             -- LINKED | UNLINKED
    CONSTRAINT fk_link_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_link_device FOREIGN KEY (smart_device_id) REFERENCES smart_devices(id)
);

CREATE TABLE activity_records (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT      NOT NULL,
    smart_device_id BIGINT      NOT NULL,
    steps           INTEGER     NOT NULL,
    heart_rate      INTEGER,
    recorded_at     DATETIME(6) NOT NULL,
    CONSTRAINT fk_activity_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_device FOREIGN KEY (smart_device_id) REFERENCES smart_devices(id)
);
CREATE INDEX ix_activity_student_time ON activity_records(student_id, recorded_at);

CREATE TABLE urec_card_readers (
    reader_id VARCHAR(40) NOT NULL PRIMARY KEY,
    location  VARCHAR(80) NOT NULL
);

CREATE TABLE rec_visits (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT      NOT NULL,
    reader_id  VARCHAR(40) NOT NULL,
    visited_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_visit_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_visit_reader FOREIGN KEY (reader_id) REFERENCES urec_card_readers(reader_id)
);
CREATE INDEX ix_visit_student_time ON rec_visits(student_id, visited_at);

CREATE TABLE friend_requests (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sender_id   BIGINT      NOT NULL,
    receiver_id BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL,                -- PENDING | ACCEPTED | DECLINED
    sent_at     DATETIME(6) NOT NULL,
    CONSTRAINT fk_freq_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_freq_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE friendships (
    id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_a_id BIGINT      NOT NULL,
    user_b_id BIGINT      NOT NULL,
    since     DATETIME(6) NOT NULL,
    CONSTRAINT uq_friendship UNIQUE (user_a_id, user_b_id),
    CONSTRAINT fk_friend_a FOREIGN KEY (user_a_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friend_b FOREIGN KEY (user_b_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE consent_settings (
    student_id     BIGINT  NOT NULL PRIMARY KEY,
    share_calories BOOLEAN NOT NULL DEFAULT FALSE,
    share_water    BOOLEAN NOT NULL DEFAULT FALSE,
    share_steps    BOOLEAN NOT NULL DEFAULT FALSE,
    share_visits   BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_consent_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE point_accounts (
    student_id BIGINT  NOT NULL PRIMARY KEY,
    balance    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_points_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE point_transactions (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT      NOT NULL,
    amount     INTEGER     NOT NULL,
    reason     VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ptx_account FOREIGN KEY (student_id) REFERENCES point_accounts(student_id) ON DELETE CASCADE
);

CREATE TABLE app_modules (
    module_id VARCHAR(32) NOT NULL PRIMARY KEY,
    name      VARCHAR(80) NOT NULL,
    enabled   BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE uptime_records (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    checked_at   DATETIME(6) NOT NULL,
    service_name VARCHAR(40) NOT NULL,
    is_up        BOOLEAN     NOT NULL,
    response_ms  INTEGER     NOT NULL
);
CREATE INDEX ix_uptime_time ON uptime_records(checked_at);

-- Seed: app modules a developer can disable (FR-5, A-11)
INSERT INTO app_modules (module_id, name, enabled) VALUES ('NUTRITION', 'Nutrition & Hydration', TRUE);
INSERT INTO app_modules (module_id, name, enabled) VALUES ('ACTIVITY', 'Activity & UREC Visits', TRUE);
INSERT INTO app_modules (module_id, name, enabled) VALUES ('SOCIAL', 'Friends & Leaderboard', TRUE);

-- Seed: one registered UREC card reader (UC-2, A-12)
INSERT INTO urec_card_readers (reader_id, location) VALUES ('UREC-FRONT-1', 'UREC front desk');
