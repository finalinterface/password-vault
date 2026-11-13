CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    login TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created BIGINT NOT NULL
);

ALTER TABLE password_entries
    ADD COLUMN user_id BIGINT NULL REFERENCES users (id);

DROP INDEX idx_password_entries_not_deleted;

CREATE INDEX idx_password_entries_user_not_deleted
    ON password_entries (user_id, id)
    WHERE deleted IS NULL;
