ALTER TABLE users
ADD COLUMN pending_email VARCHAR(255);

ALTER TABLE users
ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    target_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_email_verification_tokens_token
        UNIQUE (token)
);

INSERT INTO email_verification_tokens (
    token,
    user_id,
    type,
    target_email,
    created_at,
    is_valid
)
SELECT
    verification_token,
    id,
    'REGISTRATION',
    email,
    CURRENT_TIMESTAMP,
    NOT email_verified
FROM users
WHERE verification_token IS NOT NULL;

ALTER TABLE users
DROP COLUMN verification_token;

CREATE UNIQUE INDEX uk_users_pending_email
    ON users(pending_email)
    WHERE pending_email IS NOT NULL;