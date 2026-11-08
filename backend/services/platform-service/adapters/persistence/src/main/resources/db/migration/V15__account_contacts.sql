-- 邮箱与手机号是全局账号联系资料，默认为空；未验证前不能作为登录识别符。
ALTER TABLE identity_account
    ADD COLUMN email VARCHAR(254) NULL AFTER display_name,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER email,
    ADD COLUMN phone_number VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER email_verified,
    ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER phone_number,
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD CONSTRAINT uk_identity_account_email UNIQUE (email),
    ADD CONSTRAINT uk_identity_account_phone UNIQUE (phone_number),
    ADD CONSTRAINT ck_identity_account_email_verified CHECK (email IS NOT NULL OR email_verified = FALSE),
    ADD CONSTRAINT ck_identity_account_phone_verified CHECK (phone_number IS NOT NULL OR phone_verified = FALSE);
