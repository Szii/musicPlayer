CREATE TABLE users (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(255) NOT NULL UNIQUE,
    password  VARCHAR(255) NOT NULL
);

CREATE TABLE groups (
    id         BIGSERIAL PRIMARY KEY,
    list_name  VARCHAR(255) NOT NULL,
    owner_id   BIGINT       NOT NULL REFERENCES users(id)
);

CREATE TABLE tracks (
    id          BIGSERIAL PRIMARY KEY,
    track_name  VARCHAR(255) NOT NULL,
    track_link  VARCHAR(1024) NOT NULL,
    group_id    BIGINT REFERENCES groups(id),
    duration    INTEGER NOT NULL,
    owner_id    BIGINT NOT NULL REFERENCES users(id)
);

CREATE TABLE track_points (
    id         BIGSERIAL PRIMARY KEY,
    track_id   BIGINT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    position   BIGINT NOT NULL,
    fade_in    BOOLEAN NOT NULL,
    fade_out   BOOLEAN NOT NULL
);

CREATE TABLE boards (
    id                 BIGSERIAL PRIMARY KEY,
    selected_track_id  BIGINT REFERENCES tracks(id),
    volume             INTEGER NOT NULL,
    repeat             BOOLEAN NOT NULL,
    current_position   INTEGER NOT NULL,
    overplay           BOOLEAN NOT NULL,
    owner_id           BIGINT NOT NULL REFERENCES users(id)
);

CREATE TABLE user_track_access (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES users(id),
    track_id  BIGINT NOT NULL REFERENCES tracks(id),
    group_id  BIGINT NOT NULL REFERENCES groups(id),
    CONSTRAINT uk_user_track_group UNIQUE (user_id, track_id, group_id)
);


CREATE TABLE user_tracks (
    user_id  BIGINT NOT NULL REFERENCES users(id),
    track_id BIGINT NOT NULL REFERENCES tracks(id),
    PRIMARY KEY (user_id, track_id)
);


CREATE TABLE user_groups (
    user_id  BIGINT NOT NULL REFERENCES users(id),
    group_id BIGINT NOT NULL REFERENCES groups(id),
    PRIMARY KEY (user_id, group_id)
);