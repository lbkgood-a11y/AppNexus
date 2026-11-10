-- 邀请绑定已有全局账号，接受前不创建有效成员或授予业务权限；不以未验证邮箱作为身份依据。
CREATE TABLE tenant_invitation (
    -- 不可变邀请 UUID，查询标识本身不能用于接受邀请。
    id BINARY(16) NOT NULL PRIMARY KEY,
    -- 固定目标租户，接受时再次复核启用状态。
    tenant_id BINARY(16) NOT NULL,
    -- 固定已有账号，接受时必须与当前认证账号一致。
    account_id BINARY(16) NOT NULL,
    -- 256 位随机邀请凭证的 SHA-256 摘要，原文仅创建时返回一次。
    token_hash BINARY(32) NOT NULL,
    -- PENDING/ACCEPTED/REVOKED，状态只能受控推进，接受后不能再次使用。
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- 到期 UTC 时间，创建后 24 小时，不允许客户端无限延长。
    expires_at TIMESTAMP(6) NOT NULL,
    -- 受权平台操作者，历史审计来源，不因其后续停用自动扩权。
    invited_by BINARY(16) NOT NULL,
    -- 服务端 UTC 创建时间。
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_invitation_tenant (tenant_id,created_at),
    INDEX ix_invitation_account (account_id,status,expires_at)
);
