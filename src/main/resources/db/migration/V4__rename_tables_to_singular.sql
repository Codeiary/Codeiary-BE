ALTER TABLE users RENAME TO "user";
ALTER TABLE refresh_tokens RENAME TO refresh_token;
ALTER TABLE blog_posts RENAME TO blog_post;

ALTER INDEX uk_users_nickname RENAME TO uk_user_nickname;
ALTER INDEX uk_users_oauth_identity RENAME TO uk_user_oauth_identity;
ALTER INDEX idx_refresh_tokens_user_id RENAME TO idx_refresh_token_user_id;
ALTER INDEX idx_refresh_tokens_expires_at RENAME TO idx_refresh_token_expires_at;
ALTER INDEX idx_refresh_tokens_session_id RENAME TO idx_refresh_token_session_id;
ALTER INDEX idx_blog_posts_author_id RENAME TO idx_blog_post_author_id;
ALTER INDEX idx_blog_posts_created_at RENAME TO idx_blog_post_created_at;
ALTER INDEX idx_blog_posts_category RENAME TO idx_blog_post_category;
