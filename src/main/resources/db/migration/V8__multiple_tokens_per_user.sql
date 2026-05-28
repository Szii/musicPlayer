CREATE TABLE tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    target_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_tokens_token
        UNIQUE (token),

    CONSTRAINT uk_tokens_user_type
        UNIQUE (user_id, type)
);

INSERT INTO tokens (
    token,
    user_id,
    type,
    target_email,
    created_at,
    is_valid
)
SELECT
    evt.token,
    evt.user_id,
    COALESCE(evt.type, 'REGISTRATION'),
    COALESCE(evt.target_email, u.email),
    evt.created_at,
    evt.is_valid
FROM email_verification_tokens evt
JOIN users u ON u.id = evt.user_id;

DROP TABLE email_verification_tokens;

CREATE INDEX idx_tokens_user_id
    ON tokens(user_id);

CREATE INDEX idx_tokens_token_valid
    ON tokens(token, is_valid);