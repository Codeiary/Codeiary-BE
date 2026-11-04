UPDATE font SET css_family = '"Noto Sans KR", "DM Sans", sans-serif',
    stylesheet_url = 'https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700;800&display=swap',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'sans';

UPDATE font SET css_family = '"Roboto Mono", "Noto Sans KR", monospace',
    stylesheet_url = 'https://fonts.googleapis.com/css2?family=Roboto+Mono:wght@400;500;600;700&display=swap',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'mono';
