ALTER TABLE users
    ADD COLUMN github_url VARCHAR(255),
    ADD COLUMN contact_email VARCHAR(254),
    ADD COLUMN oauth_provider VARCHAR(20),
    ADD COLUMN oauth_subject VARCHAR(255),
    ADD CONSTRAINT ck_users_oauth_identity CHECK (
        (oauth_provider IS NULL AND oauth_subject IS NULL)
        OR (oauth_provider IS NOT NULL AND oauth_subject IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_users_oauth_identity ON users (oauth_provider, oauth_subject);

ALTER TABLE users
    DROP CONSTRAINT ck_users_role,
    ADD CONSTRAINT ck_users_role CHECK (role IN ('PENDING', 'USER', 'ADMIN')),
    ALTER COLUMN role SET DEFAULT 'PENDING';

UPDATE users
SET role = 'PENDING'
WHERE role = 'USER' AND (nickname IS NULL OR btrim(nickname) = '');
