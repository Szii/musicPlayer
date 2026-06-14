ALTER TABLE track_windows
ADD COLUMN position_within_track INT;

WITH ordered_windows AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY track_id
            ORDER BY position_from ASC, id ASC
        ) AS new_position
    FROM track_windows
)
UPDATE track_windows tw
SET position_within_track = ow.new_position
FROM ordered_windows ow
WHERE tw.id = ow.id;

ALTER TABLE track_windows
ALTER COLUMN position_within_track SET NOT NULL;

ALTER TABLE track_windows
ADD CONSTRAINT uk_track_window_position
UNIQUE (track_id, position_within_track);