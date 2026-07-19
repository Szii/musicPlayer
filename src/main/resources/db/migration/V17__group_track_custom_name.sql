ALTER TABLE group_tracks ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE group_tracks DROP CONSTRAINT pk_group_tracks;
ALTER TABLE group_tracks ADD PRIMARY KEY (id);
ALTER TABLE group_tracks ADD CONSTRAINT uq_group_track UNIQUE (group_id, track_id);

ALTER TABLE group_tracks ADD COLUMN custom_name varchar(255);
