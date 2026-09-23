ALTER TABLE group_tracks ADD COLUMN track_window_id uuid;
ALTER TABLE group_tracks ADD CONSTRAINT fk_gt_window
    FOREIGN KEY (track_window_id) REFERENCES track_windows(id) ON DELETE CASCADE;

ALTER TABLE group_tracks DROP CONSTRAINT uq_group_track;
CREATE UNIQUE INDEX uq_group_track_only ON group_tracks (group_id, track_id)
    WHERE track_window_id IS NULL;
CREATE UNIQUE INDEX uq_group_window ON group_tracks (group_id, track_window_id)
    WHERE track_window_id IS NOT NULL;

CREATE INDEX idx_gt_window ON group_tracks (track_window_id);
