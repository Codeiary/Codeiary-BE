CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_blog_post_title_trgm
    ON blog_post USING GIN (lower(title) gin_trgm_ops);

CREATE INDEX idx_blog_post_content_trgm
    ON blog_post USING GIN (lower(content) gin_trgm_ops);
