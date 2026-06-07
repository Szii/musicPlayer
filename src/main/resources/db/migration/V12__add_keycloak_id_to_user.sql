ALTER TABLE users
ADD COLUMN keycloak_id UUID;

CREATE UNIQUE INDEX ux_users_keycloak_id
ON users (keycloak_id)
WHERE keycloak_id IS NOT NULL;