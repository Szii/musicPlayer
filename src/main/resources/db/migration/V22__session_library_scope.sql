CREATE TABLE session_tracks (
    session_id UUID NOT NULL,
    track_id   UUID NOT NULL,

    CONSTRAINT pk_session_tracks PRIMARY KEY (session_id, track_id),
    CONSTRAINT fk_session_tracks_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_tracks_track
        FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_tracks_track_id ON session_tracks(track_id);

CREATE TABLE session_groups (
    session_id UUID NOT NULL,
    group_id   UUID NOT NULL,

    CONSTRAINT pk_session_groups PRIMARY KEY (session_id, group_id),
    CONSTRAINT fk_session_groups_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_groups_group
        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_groups_group_id ON session_groups(group_id);

INSERT INTO session_tracks (session_id, track_id)
SELECT DISTINCT b.session_id, b.selected_track_id
FROM boards b
WHERE b.selected_track_id IS NOT NULL;

INSERT INTO session_groups (session_id, group_id)
SELECT DISTINCT b.session_id, b.selected_group_id
FROM boards b
WHERE b.selected_group_id IS NOT NULL;
