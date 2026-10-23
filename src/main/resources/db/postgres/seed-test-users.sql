

INSERT INTO users (login, email, hash_password)
SELECT 'demo_friend_' || lpad(n::text, 2, '0'),
       'demo_friend_' || lpad(n::text, 2, '0') || '@example.test',
       '$2y$10$QsPdkrUY8AsDDkSxVE9Of..pPR8e.zNWbpeb4NRkLsiZ6ABqM3A.y'
FROM generate_series(1, 44) AS numbers(n)
ON CONFLICT DO NOTHING;
