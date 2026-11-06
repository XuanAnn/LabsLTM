USE ltm_mail;
SELECT name,email,password,last_login,last_logout FROM users;
SELECT id,recipient,sender,LENGTH(body) AS bytes,created_at FROM mail ORDER BY id DESC;
SELECT id,owner,sender,recipient,uid,created_at FROM sent_mail ORDER BY id DESC;
SELECT id,CONVERT(body USING utf8mb4) AS message_content FROM sent_mail ORDER BY id DESC;
SELECT * FROM sessions ORDER BY connected_at DESC;
