-- Font files are versioned frontend assets, loaded through their bundled CSS.
UPDATE font SET stylesheet_url = NULL, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('sans', 'serif', 'mono', 'noto-sans', 'nanum-gothic', 'nanum-myeongjo',
               'gowun-dodum', 'gowun-batang', 'jua', 'do-hyeon', 'black-han', 'nanum-pen');
