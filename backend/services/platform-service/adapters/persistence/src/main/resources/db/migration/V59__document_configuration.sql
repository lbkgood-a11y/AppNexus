-- 单据目录由代码声明、平台激活；默认关闭，不自动授权存量租户。
CREATE TABLE doc_type (
 type_code VARCHAR(64) PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0
);
-- 按单据类型授予租户配置资格；维护资格与普通用户业务授权分开。
CREATE TABLE doc_tenant_grant (
 tenant_id VARCHAR(36) NOT NULL,
 type_code VARCHAR(64) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 PRIMARY KEY(tenant_id,type_code)
);
-- 每租户每类型原子发布；draft 为草稿，published 为不可变发布快照。
CREATE TABLE doc_configuration (
 tenant_id VARCHAR(36) NOT NULL,
 type_code VARCHAR(64) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 published_version INT NOT NULL DEFAULT 0,
 draft JSON NOT NULL,
 published JSON NULL,
 PRIMARY KEY(tenant_id,type_code)
);
-- 历史配置不可覆盖，供实例追溯。
CREATE TABLE doc_configuration_version (
 tenant_id VARCHAR(36) NOT NULL,
 type_code VARCHAR(64) NOT NULL,
 version INT NOT NULL,
 descriptor_version INT NOT NULL,
 bindings JSON NOT NULL,
 actor VARCHAR(36) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,type_code,version)
);
-- 非敏感解析快照和请求键持久化；同键不得改选目标流程。
CREATE TABLE doc_resolution (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id VARCHAR(36) NOT NULL,
 actor VARCHAR(36) NOT NULL,
 request_key VARCHAR(36) NOT NULL,
 type_code VARCHAR(64) NOT NULL,
 object_id VARCHAR(36) NOT NULL,
 basis_version BIGINT NOT NULL,
 configuration_version INT NOT NULL,
 definition_id VARCHAR(64) NOT NULL,
 definition_version INT NOT NULL,
 facts JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_doc_resolution(tenant_id,actor,request_key)
);
-- 管理审计仅保存配置，不记录单据原文或凭据。
CREATE TABLE doc_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 tenant_id VARCHAR(36) NULL,
 actor VARCHAR(36) NOT NULL,
 type_code VARCHAR(64) NOT NULL,
 action VARCHAR(32) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
