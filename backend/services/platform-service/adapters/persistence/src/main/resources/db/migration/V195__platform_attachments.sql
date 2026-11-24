-- 附件类型的全局上传规则。格式检测及硬上限由应用目录约束。
CREATE TABLE platform_attachment_upload_policy (
    type_key VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '稳定文件类型键',
    enabled BOOLEAN NOT NULL COMMENT '是否允许新上传',
    max_bytes BIGINT NOT NULL CHECK (max_bytes > 0 AND max_bytes <= 20971520) COMMENT '单文件最大字节数',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '规则乐观锁版本，从零开始',
    created_by BINARY(16) NULL COMMENT '创建账号，系统初始化为空',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的UTC创建时间',
    updated_by BINARY(16) NULL COMMENT '最近修改账号，系统初始化为空',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的UTC最近修改时间'
) COMMENT '平台附件上传规则';

-- 20 MiB 和 10 MiB 分别为 20971520 和 10485760 字节。
INSERT INTO platform_attachment_upload_policy(type_key, enabled, max_bytes) VALUES
    ('jpeg', TRUE, 10485760), ('png', TRUE, 10485760), ('pdf', TRUE, 20971520),
    ('docx', TRUE, 20971520), ('xlsx', TRUE, 20971520), ('pptx', TRUE, 20971520),
    ('txt', FALSE, 10485760), ('csv', FALSE, 10485760);

-- 附件独立记录。关联标识由调用方提供，查询时仍执行可信租户与用户范围。
CREATE TABLE platform_attachment (
    id BINARY(16) PRIMARY KEY COMMENT '平台生成的附件标识',
    tenant_id BINARY(16) NOT NULL COMMENT '服务端验证的租户标识',
    business_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '租户内稳定的业务关联标识',
    file_name VARCHAR(255) NOT NULL COMMENT '用于展示的原始文件名称',
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 20971520) COMMENT '实际附件字节数',
    type_key VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务器识别的类型键',
    media_type VARCHAR(128) CHARACTER SET ascii NOT NULL COMMENT '服务器识别的媒体类型',
    content_sha256 BINARY(32) NOT NULL COMMENT '校验幂等重试内容的SHA-256摘要',
    storage_path VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成的私有桶内对象键',
    upload_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        CHECK (upload_status IN ('WRITING', 'AVAILABLE', 'FAILED')) COMMENT '写入中、可用或失败',
    write_token BINARY(16) NULL COMMENT '写入租约令牌，防止过期请求覆盖后续处理状态',
    idempotency_key BINARY(16) NOT NULL COMMENT '本次上传重试使用的幂等标识',
    created_by BINARY(16) NOT NULL COMMENT '通过可信认证上下文取得的上传账号',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的UTC创建时间',
    updated_by BINARY(16) NULL COMMENT '最近修改账号，系统补偿可为空',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的UTC最近修改时间',
    UNIQUE KEY uq_attachment_path (storage_path),
    UNIQUE KEY uq_attachment_upload (tenant_id, created_by, idempotency_key),
    KEY ix_attachment_business (tenant_id, business_id, upload_status, created_at, id),
    KEY ix_attachment_owner (tenant_id, created_by, created_at, id),
    KEY ix_attachment_ledger (created_at, id),
    KEY ix_attachment_tenant_ledger (tenant_id, created_at, id),
    KEY ix_attachment_recovery (upload_status, updated_at, id)
) DEFAULT CHARACTER SET utf8mb4 COMMENT '租户附件资料';
