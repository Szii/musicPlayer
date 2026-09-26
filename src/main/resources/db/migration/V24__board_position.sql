ALTER TABLE boards ADD COLUMN position_within_session integer;

WITH ordered AS (
    SELECT id, row_number() OVER (PARTITION BY session_id ORDER BY lower(name), id) AS rn
    FROM boards
)
UPDATE boards b SET position_within_session = ordered.rn
FROM ordered WHERE b.id = ordered.id;

ALTER TABLE boards ALTER COLUMN position_within_session SET NOT NULL;
