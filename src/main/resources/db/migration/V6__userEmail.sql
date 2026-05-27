ALTER TABLE users
ADD COLUMN email VARCHAR(255),
ADD COLUMN verification_token VARCHAR(255) DEFAULT NULL,
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET email = CONCAT('user', id, '@example.com')
WHERE email IS NULL;

UPDATE users
SET email_verified = TRUE;

UPDATE users
SET verification_token = NULL;

ALTER TABLE users
ALTER COLUMN email SET NOT NULL;
