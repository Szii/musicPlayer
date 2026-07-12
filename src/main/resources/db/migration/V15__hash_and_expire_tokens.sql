DELETE FROM tokens;

ALTER TABLE tokens
    DROP COLUMN is_valid,
    DROP COLUMN token,
    ADD COLUMN token_hash VARCHAR(64) NOT NULL,
    ADD COLUMN expires_at TIMESTAMP NOT NULL;

CREATE UNIQUE INDEX ux_tokens_token_hash ON tokens (token_hash);
