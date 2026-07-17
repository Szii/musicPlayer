ALTER TABLE users        ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE groups       ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE track_shares ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE tracks       ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE track_windows ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE sessions     ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE boards       ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE tokens       ADD COLUMN new_id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE groups ADD COLUMN new_owner_id uuid;
UPDATE groups c SET new_owner_id = u.new_id FROM users u WHERE c.owner_id = u.id;

ALTER TABLE tracks ADD COLUMN new_owner_id uuid;
UPDATE tracks c SET new_owner_id = u.new_id FROM users u WHERE c.owner_id = u.id;
ALTER TABLE tracks ADD COLUMN new_track_share_id uuid;
UPDATE tracks c SET new_track_share_id = s.new_id FROM track_shares s WHERE c.track_share_id = s.id;

ALTER TABLE track_windows ADD COLUMN new_track_id uuid;
UPDATE track_windows c SET new_track_id = t.new_id FROM tracks t WHERE c.track_id = t.id;

ALTER TABLE sessions ADD COLUMN new_owner_id uuid;
UPDATE sessions c SET new_owner_id = u.new_id FROM users u WHERE c.owner_id = u.id;

ALTER TABLE boards ADD COLUMN new_owner_id uuid;
UPDATE boards c SET new_owner_id = u.new_id FROM users u WHERE c.owner_id = u.id;
ALTER TABLE boards ADD COLUMN new_session_id uuid;
UPDATE boards c SET new_session_id = s.new_id FROM sessions s WHERE c.session_id = s.id;
ALTER TABLE boards ADD COLUMN new_selected_track_id uuid;
UPDATE boards c SET new_selected_track_id = t.new_id FROM tracks t WHERE c.selected_track_id = t.id;
ALTER TABLE boards ADD COLUMN new_selected_group_id uuid;
UPDATE boards c SET new_selected_group_id = g.new_id FROM groups g WHERE c.selected_group_id = g.id;
ALTER TABLE boards ADD COLUMN new_selected_window_id uuid;
UPDATE boards c SET new_selected_window_id = w.new_id FROM track_windows w WHERE c.selected_window_id = w.id;

ALTER TABLE tokens ADD COLUMN new_user_id uuid;
UPDATE tokens c SET new_user_id = u.new_id FROM users u WHERE c.user_id = u.id;

ALTER TABLE group_tracks ADD COLUMN new_group_id uuid, ADD COLUMN new_track_id uuid;
UPDATE group_tracks c SET new_group_id = g.new_id FROM groups g WHERE c.group_id = g.id;
UPDATE group_tracks c SET new_track_id = t.new_id FROM tracks t WHERE c.track_id = t.id;

ALTER TABLE user_tracks ADD COLUMN new_user_id uuid, ADD COLUMN new_track_id uuid;
UPDATE user_tracks c SET new_user_id = u.new_id FROM users u WHERE c.user_id = u.id;
UPDATE user_tracks c SET new_track_id = t.new_id FROM tracks t WHERE c.track_id = t.id;

ALTER TABLE user_groups ADD COLUMN new_user_id uuid, ADD COLUMN new_group_id uuid;
UPDATE user_groups c SET new_user_id = u.new_id FROM users u WHERE c.user_id = u.id;
UPDATE user_groups c SET new_group_id = g.new_id FROM groups g WHERE c.group_id = g.id;

ALTER TABLE user_subscribed_tracks ADD COLUMN new_user_id uuid, ADD COLUMN new_track_id uuid;
UPDATE user_subscribed_tracks c SET new_user_id = u.new_id FROM users u WHERE c.user_id = u.id;
UPDATE user_subscribed_tracks c SET new_track_id = t.new_id FROM tracks t WHERE c.track_id = t.id;

ALTER TABLE user_shares ADD COLUMN new_user_id uuid, ADD COLUMN new_share_id uuid;
UPDATE user_shares c SET new_user_id = u.new_id FROM users u WHERE c.user_id = u.id;
UPDATE user_shares c SET new_share_id = s.new_id FROM track_shares s WHERE c.share_id = s.id;

ALTER TABLE groups        DROP CONSTRAINT IF EXISTS fk_group_owner;
ALTER TABLE tracks        DROP CONSTRAINT IF EXISTS fk_track_owner;
ALTER TABLE tracks        DROP CONSTRAINT IF EXISTS fk_track_share;
ALTER TABLE track_windows DROP CONSTRAINT IF EXISTS fk_point_window;
ALTER TABLE sessions      DROP CONSTRAINT IF EXISTS fk_sessions_owner;
ALTER TABLE boards        DROP CONSTRAINT IF EXISTS fk_board_owner;
ALTER TABLE boards        DROP CONSTRAINT IF EXISTS fk_board_track;
ALTER TABLE boards        DROP CONSTRAINT IF EXISTS fk_boards_session;
ALTER TABLE tokens        DROP CONSTRAINT IF EXISTS fk_tokens_user;
ALTER TABLE group_tracks  DROP CONSTRAINT IF EXISTS fk_gt_group;
ALTER TABLE group_tracks  DROP CONSTRAINT IF EXISTS fk_gt_track;
ALTER TABLE user_tracks   DROP CONSTRAINT IF EXISTS fk_ut_user;
ALTER TABLE user_tracks   DROP CONSTRAINT IF EXISTS fk_ut_track;
ALTER TABLE user_groups   DROP CONSTRAINT IF EXISTS fk_ug_user;
ALTER TABLE user_groups   DROP CONSTRAINT IF EXISTS fk_ug_group;
ALTER TABLE user_subscribed_tracks DROP CONSTRAINT IF EXISTS fk_user_subscribed;
ALTER TABLE user_subscribed_tracks DROP CONSTRAINT IF EXISTS fk_track_subscribed;
ALTER TABLE user_shares   DROP CONSTRAINT IF EXISTS fk_user_subscribed;
ALTER TABLE user_shares   DROP CONSTRAINT IF EXISTS fk_share_subscribed;

ALTER TABLE group_tracks           DROP CONSTRAINT IF EXISTS pk_group_tracks;
ALTER TABLE user_tracks            DROP CONSTRAINT IF EXISTS pk_user_tracks;
ALTER TABLE user_groups            DROP CONSTRAINT IF EXISTS pk_user_groups;
ALTER TABLE user_subscribed_tracks DROP CONSTRAINT IF EXISTS user_subscribed_tracks_pkey;
ALTER TABLE user_shares            DROP CONSTRAINT IF EXISTS user_shares_pkey;

ALTER TABLE users        DROP CONSTRAINT users_pkey;
ALTER TABLE users        DROP COLUMN id;
ALTER TABLE users        RENAME COLUMN new_id TO id;
ALTER TABLE users        ALTER COLUMN id DROP DEFAULT;
ALTER TABLE users        ADD PRIMARY KEY (id);

ALTER TABLE groups       DROP CONSTRAINT groups_pkey;
ALTER TABLE groups       DROP COLUMN id;
ALTER TABLE groups       RENAME COLUMN new_id TO id;
ALTER TABLE groups       ALTER COLUMN id DROP DEFAULT;
ALTER TABLE groups       ADD PRIMARY KEY (id);

ALTER TABLE track_shares DROP CONSTRAINT track_shares_pkey;
ALTER TABLE track_shares DROP COLUMN id;
ALTER TABLE track_shares RENAME COLUMN new_id TO id;
ALTER TABLE track_shares ALTER COLUMN id DROP DEFAULT;
ALTER TABLE track_shares ADD PRIMARY KEY (id);

ALTER TABLE tracks       DROP CONSTRAINT tracks_pkey;
ALTER TABLE tracks       DROP COLUMN id;
ALTER TABLE tracks       RENAME COLUMN new_id TO id;
ALTER TABLE tracks       ALTER COLUMN id DROP DEFAULT;
ALTER TABLE tracks       ADD PRIMARY KEY (id);

ALTER TABLE track_windows DROP CONSTRAINT track_windows_pkey;
ALTER TABLE track_windows DROP COLUMN id;
ALTER TABLE track_windows RENAME COLUMN new_id TO id;
ALTER TABLE track_windows ALTER COLUMN id DROP DEFAULT;
ALTER TABLE track_windows ADD PRIMARY KEY (id);

ALTER TABLE sessions     DROP CONSTRAINT sessions_pkey;
ALTER TABLE sessions     DROP COLUMN id;
ALTER TABLE sessions     RENAME COLUMN new_id TO id;
ALTER TABLE sessions     ALTER COLUMN id DROP DEFAULT;
ALTER TABLE sessions     ADD PRIMARY KEY (id);

ALTER TABLE boards       DROP CONSTRAINT boards_pkey;
ALTER TABLE boards       DROP COLUMN id;
ALTER TABLE boards       RENAME COLUMN new_id TO id;
ALTER TABLE boards       ALTER COLUMN id DROP DEFAULT;
ALTER TABLE boards       ADD PRIMARY KEY (id);

ALTER TABLE tokens       DROP CONSTRAINT tokens_pkey;
ALTER TABLE tokens       DROP COLUMN id;
ALTER TABLE tokens       RENAME COLUMN new_id TO id;
ALTER TABLE tokens       ALTER COLUMN id DROP DEFAULT;
ALTER TABLE tokens       ADD PRIMARY KEY (id);

-- Phase 3d: swap foreign-key columns
ALTER TABLE groups DROP COLUMN owner_id;
ALTER TABLE groups RENAME COLUMN new_owner_id TO owner_id;
ALTER TABLE groups ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE tracks DROP COLUMN owner_id;
ALTER TABLE tracks RENAME COLUMN new_owner_id TO owner_id;
ALTER TABLE tracks ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE tracks DROP COLUMN track_share_id;
ALTER TABLE tracks RENAME COLUMN new_track_share_id TO track_share_id;

ALTER TABLE track_windows DROP COLUMN track_id;
ALTER TABLE track_windows RENAME COLUMN new_track_id TO track_id;
ALTER TABLE track_windows ALTER COLUMN track_id SET NOT NULL;

ALTER TABLE sessions DROP COLUMN owner_id;
ALTER TABLE sessions RENAME COLUMN new_owner_id TO owner_id;
ALTER TABLE sessions ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE boards DROP COLUMN owner_id;
ALTER TABLE boards RENAME COLUMN new_owner_id TO owner_id;
ALTER TABLE boards ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE boards DROP COLUMN session_id;
ALTER TABLE boards RENAME COLUMN new_session_id TO session_id;
ALTER TABLE boards ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE boards DROP COLUMN selected_track_id;
ALTER TABLE boards RENAME COLUMN new_selected_track_id TO selected_track_id;
ALTER TABLE boards DROP COLUMN selected_group_id;
ALTER TABLE boards RENAME COLUMN new_selected_group_id TO selected_group_id;
ALTER TABLE boards DROP COLUMN selected_window_id;
ALTER TABLE boards RENAME COLUMN new_selected_window_id TO selected_window_id;

ALTER TABLE tokens DROP COLUMN user_id;
ALTER TABLE tokens RENAME COLUMN new_user_id TO user_id;
ALTER TABLE tokens ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE group_tracks DROP COLUMN group_id, DROP COLUMN track_id;
ALTER TABLE group_tracks RENAME COLUMN new_group_id TO group_id;
ALTER TABLE group_tracks RENAME COLUMN new_track_id TO track_id;
ALTER TABLE group_tracks ALTER COLUMN group_id SET NOT NULL;
ALTER TABLE group_tracks ALTER COLUMN track_id SET NOT NULL;

ALTER TABLE user_tracks DROP COLUMN user_id, DROP COLUMN track_id;
ALTER TABLE user_tracks RENAME COLUMN new_user_id TO user_id;
ALTER TABLE user_tracks RENAME COLUMN new_track_id TO track_id;
ALTER TABLE user_tracks ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_tracks ALTER COLUMN track_id SET NOT NULL;

ALTER TABLE user_groups DROP COLUMN user_id, DROP COLUMN group_id;
ALTER TABLE user_groups RENAME COLUMN new_user_id TO user_id;
ALTER TABLE user_groups RENAME COLUMN new_group_id TO group_id;
ALTER TABLE user_groups ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_groups ALTER COLUMN group_id SET NOT NULL;

ALTER TABLE user_subscribed_tracks DROP COLUMN user_id, DROP COLUMN track_id;
ALTER TABLE user_subscribed_tracks RENAME COLUMN new_user_id TO user_id;
ALTER TABLE user_subscribed_tracks RENAME COLUMN new_track_id TO track_id;
ALTER TABLE user_subscribed_tracks ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_subscribed_tracks ALTER COLUMN track_id SET NOT NULL;

ALTER TABLE user_shares DROP COLUMN user_id, DROP COLUMN share_id;
ALTER TABLE user_shares RENAME COLUMN new_user_id TO user_id;
ALTER TABLE user_shares RENAME COLUMN new_share_id TO share_id;
ALTER TABLE user_shares ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_shares ALTER COLUMN share_id SET NOT NULL;


ALTER TABLE group_tracks           ADD CONSTRAINT pk_group_tracks PRIMARY KEY (group_id, track_id);
ALTER TABLE user_tracks            ADD CONSTRAINT pk_user_tracks PRIMARY KEY (user_id, track_id);
ALTER TABLE user_groups            ADD CONSTRAINT pk_user_groups PRIMARY KEY (user_id, group_id);
ALTER TABLE user_subscribed_tracks ADD PRIMARY KEY (user_id, track_id);
ALTER TABLE user_shares            ADD PRIMARY KEY (user_id, share_id);

ALTER TABLE tracks ADD CONSTRAINT tracks_track_share_id_key UNIQUE (track_share_id);
ALTER TABLE track_windows ADD CONSTRAINT uk_track_window_position UNIQUE (track_id, position_within_track);

ALTER TABLE groups ADD CONSTRAINT fk_group_owner FOREIGN KEY (owner_id) REFERENCES users(id);
ALTER TABLE tracks ADD CONSTRAINT fk_track_owner FOREIGN KEY (owner_id) REFERENCES users(id);
ALTER TABLE tracks ADD CONSTRAINT fk_track_share FOREIGN KEY (track_share_id) REFERENCES track_shares(id) ON DELETE SET NULL;
ALTER TABLE track_windows ADD CONSTRAINT fk_point_window FOREIGN KEY (track_id) REFERENCES tracks(id);
ALTER TABLE sessions ADD CONSTRAINT fk_sessions_owner FOREIGN KEY (owner_id) REFERENCES users(id);
ALTER TABLE boards ADD CONSTRAINT fk_board_owner FOREIGN KEY (owner_id) REFERENCES users(id);
ALTER TABLE boards ADD CONSTRAINT fk_board_track FOREIGN KEY (selected_track_id) REFERENCES tracks(id);
ALTER TABLE boards ADD CONSTRAINT fk_boards_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE;
ALTER TABLE tokens ADD CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE group_tracks ADD CONSTRAINT fk_gt_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE;
ALTER TABLE group_tracks ADD CONSTRAINT fk_gt_track FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE;
ALTER TABLE user_tracks ADD CONSTRAINT fk_ut_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE user_tracks ADD CONSTRAINT fk_ut_track FOREIGN KEY (track_id) REFERENCES tracks(id);
ALTER TABLE user_groups ADD CONSTRAINT fk_ug_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE user_groups ADD CONSTRAINT fk_ug_group FOREIGN KEY (group_id) REFERENCES groups(id);
ALTER TABLE user_subscribed_tracks ADD CONSTRAINT fk_user_subscribed FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_subscribed_tracks ADD CONSTRAINT fk_track_subscribed FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE;
ALTER TABLE user_shares ADD CONSTRAINT fk_user_subscribed FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_shares ADD CONSTRAINT fk_share_subscribed FOREIGN KEY (share_id) REFERENCES track_shares(id) ON DELETE CASCADE;

CREATE INDEX idx_track_owner ON tracks(owner_id);
CREATE INDEX idx_track_share ON tracks(track_share_id);
CREATE INDEX idx_group_owner ON groups(owner_id);
CREATE INDEX idx_board_owner ON boards(owner_id);
CREATE INDEX idx_board_track ON boards(selected_track_id);
CREATE INDEX idx_gt_group ON group_tracks(group_id);
CREATE INDEX idx_gt_track ON group_tracks(track_id);
CREATE INDEX idx_ut_user ON user_tracks(user_id);
CREATE INDEX idx_ut_track ON user_tracks(track_id);
CREATE INDEX idx_ug_user ON user_groups(user_id);
CREATE INDEX idx_ug_group ON user_groups(group_id);
CREATE INDEX idx_track_window_track ON track_windows(track_id);
CREATE INDEX idx_sessions_owner_id ON sessions(owner_id);
CREATE INDEX idx_boards_session_id ON boards(session_id);
CREATE INDEX idx_tokens_user_id ON tokens(user_id);
