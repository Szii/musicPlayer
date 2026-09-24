UPDATE boards b
SET selected_track_id  = NULL,
    selected_window_id = NULL
FROM tracks t
WHERE b.selected_track_id = t.id
  AND b.owner_id <> t.owner_id;

DELETE FROM group_tracks gt
USING groups g, tracks t
WHERE gt.group_id = g.id
  AND gt.track_id = t.id
  AND g.owner_id <> t.owner_id;

DELETE FROM session_tracks st
USING sessions s, tracks t
WHERE st.session_id = s.id
  AND st.track_id = t.id
  AND s.owner_id <> t.owner_id;

ALTER TABLE tracks DROP COLUMN track_share_id;
DROP TABLE IF EXISTS user_shares;
DROP TABLE IF EXISTS user_subscribed_tracks;
DROP TABLE IF EXISTS track_shares;

CREATE TABLE session_shares (
    id             UUID PRIMARY KEY,
    session_id     UUID,
    owner_id       UUID         NOT NULL,
    share_code     VARCHAR(255) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(255),
    version        INTEGER      NOT NULL,
    snapshot       JSONB        NOT NULL,
    published_at   TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    unpublished_at TIMESTAMP,

    CONSTRAINT uk_session_share_code UNIQUE (share_code),
    CONSTRAINT fk_session_share_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_share_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_session_share_active ON session_shares (session_id)
    WHERE unpublished_at IS NULL;
CREATE INDEX idx_session_share_owner ON session_shares (owner_id);

ALTER TABLE sessions ADD COLUMN subscribed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sessions ADD COLUMN source_share_id UUID;
ALTER TABLE sessions ADD COLUMN installed_version INTEGER;
ALTER TABLE sessions ADD COLUMN modified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sessions ADD CONSTRAINT fk_session_source_share
    FOREIGN KEY (source_share_id) REFERENCES session_shares(id) ON DELETE SET NULL;
CREATE INDEX idx_sessions_source_share ON sessions (source_share_id);

ALTER TABLE tracks ADD COLUMN managed_session_id UUID;
ALTER TABLE tracks ADD CONSTRAINT fk_track_managed_session
    FOREIGN KEY (managed_session_id) REFERENCES sessions(id);
CREATE INDEX idx_tracks_managed_session ON tracks (managed_session_id);

ALTER TABLE groups ADD COLUMN managed_session_id UUID;
ALTER TABLE groups ADD CONSTRAINT fk_group_managed_session
    FOREIGN KEY (managed_session_id) REFERENCES sessions(id);
CREATE INDEX idx_groups_managed_session ON groups (managed_session_id);
