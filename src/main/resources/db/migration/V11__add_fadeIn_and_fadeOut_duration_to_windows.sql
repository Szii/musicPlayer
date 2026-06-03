ALTER TABLE track_windows
    DROP COLUMN IF EXISTS fade_in,
    DROP COLUMN IF EXISTS fade_out;

ALTER TABLE track_windows
    ADD COLUMN IF NOT EXISTS fade_in_duration_ms integer DEFAULT 3000,
    ADD COLUMN IF NOT EXISTS fade_out_duration_ms integer DEFAULT 3000;

ALTER TABLE tracks
    ADD COLUMN IF NOT EXISTS fade_in_duration_ms integer DEFAULT 3000,
    ADD COLUMN IF NOT EXISTS fade_out_duration_ms integer DEFAULT 3000;

