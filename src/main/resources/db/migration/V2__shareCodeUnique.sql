CREATE TABLE user_shares (
    user_id BIGINT NOT NULL,
    share_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, share_id),
    CONSTRAINT fk_user_subscribed FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_share_subscribed FOREIGN KEY (share_id) REFERENCES track_shares(id) ON DELETE CASCADE
);

ALTER TABLE track_shares
    ADD CONSTRAINT uk_track_share_share_code UNIQUE (share_code);