ALTER TABLE group_tracks ADD COLUMN position_within_group integer;

WITH ordered AS (
    SELECT id, row_number() OVER (PARTITION BY group_id ORDER BY id) AS rn
    FROM group_tracks
)
UPDATE group_tracks gt SET position_within_group = ordered.rn
FROM ordered WHERE gt.id = ordered.id;

ALTER TABLE group_tracks ALTER COLUMN position_within_group SET NOT NULL;
ALTER TABLE group_tracks ADD CONSTRAINT uq_group_position UNIQUE (group_id, position_within_group);
