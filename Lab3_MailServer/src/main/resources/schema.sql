CREATE TABLE IF NOT EXISTS users (name VARCHAR(64) PRIMARY KEY,password VARCHAR(128) NOT NULL,email VARCHAR(254) UNIQUE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,last_login DATETIME NULL,last_logout DATETIME NULL);
SET @mail_email_missing = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='email');
SET @mail_email_sql = IF(@mail_email_missing, 'ALTER TABLE users ADD COLUMN email VARCHAR(254) UNIQUE AFTER password', 'DO 0');
PREPARE mail_email_stmt FROM @mail_email_sql;
EXECUTE mail_email_stmt;
DEALLOCATE PREPARE mail_email_stmt;

CREATE TABLE IF NOT EXISTS audit (id BIGINT AUTO_INCREMENT PRIMARY KEY,at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,event TEXT);
CREATE TABLE IF NOT EXISTS sessions (id VARCHAR(36) PRIMARY KEY,username VARCHAR(64),remote_ip VARCHAR(128),protocol VARCHAR(16),connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,disconnected_at DATETIME NULL);
CREATE TABLE IF NOT EXISTS mail (id BIGINT AUTO_INCREMENT PRIMARY KEY,recipient VARCHAR(64),sender VARCHAR(254),body LONGBLOB,uid VARCHAR(36) UNIQUE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
INSERT IGNORE INTO users(name,password) VALUES ('alice','123'),('bob','123');

CREATE TABLE IF NOT EXISTS sent_mail (id BIGINT AUTO_INCREMENT PRIMARY KEY,owner VARCHAR(64) NOT NULL,recipient LONGTEXT,sender VARCHAR(254),body LONGBLOB,uid VARCHAR(64) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY sent_owner_uid(owner,uid));
CREATE TABLE IF NOT EXISTS mail_schema_migrations (name VARCHAR(64) PRIMARY KEY);
INSERT IGNORE INTO sent_mail(owner,recipient,sender,body,uid,created_at) SELECT SUBSTRING_INDEX(sender,'@',1),CONCAT(recipient,'@localhost'),sender,body,SHA2(body,256),created_at FROM mail WHERE sender LIKE '%@localhost' AND NOT EXISTS (SELECT 1 FROM mail_schema_migrations WHERE name='sent-v1');
INSERT IGNORE INTO mail_schema_migrations(name) VALUES ('sent-v1');

UPDATE users SET email=CONCAT(name,'@localhost') WHERE email IS NULL OR email='';

SET @sent_recipients_sql = IF((SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sent_mail' AND column_name='recipient') = 'longtext', 'DO 0', 'ALTER TABLE sent_mail MODIFY COLUMN recipient LONGTEXT');
PREPARE sent_recipients_stmt FROM @sent_recipients_sql;
EXECUTE sent_recipients_stmt;
DEALLOCATE PREPARE sent_recipients_stmt;

SET @mail_ip_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='last_ip') = 0, 'ALTER TABLE users ADD COLUMN last_ip VARCHAR(128) NULL', 'DO 0');
PREPARE mail_ip_stmt FROM @mail_ip_sql;
EXECUTE mail_ip_stmt;
DEALLOCATE PREPARE mail_ip_stmt;
