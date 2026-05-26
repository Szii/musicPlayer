ALTER TABLE users
ADD COLUMN email VARCHAR(255) NOT NULL,
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users u
SET email = CONCAT('user', u.id, '@example.com')
WHERE email IS NULL;

UPDATE users u
SET email_verified = TRUE;