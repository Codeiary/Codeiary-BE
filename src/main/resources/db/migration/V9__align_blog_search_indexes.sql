DROP INDEX IF EXISTS idx_blog_post_content_trgm;

CREATE INDEX idx_category_name_trgm
    ON category USING GIN (lower(name) gin_trgm_ops);

CREATE INDEX idx_tag_name_trgm
    ON tag USING GIN (lower(name) gin_trgm_ops);
