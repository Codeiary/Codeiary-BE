ALTER TABLE github_blog_outbox
    ADD COLUMN title VARCHAR(200),
    ADD COLUMN previous_path VARCHAR(400),
    ADD COLUMN target_path VARCHAR(400);
