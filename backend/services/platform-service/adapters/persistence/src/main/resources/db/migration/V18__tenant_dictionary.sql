-- 字典类型按租户隔离；编码软删除后仍占用，防止历史记录与新语义错误关联。
CREATE TABLE tenant_dictionary (
 id BINARY(16) NOT NULL PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(100) NOT NULL,
 description VARCHAR(500) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 sort_order INT NOT NULL DEFAULT 100,
 version BIGINT NOT NULL DEFAULT 0,
 created_by BINARY(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_by BINARY(16) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted_by BINARY(16) NULL,
 deleted_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_tenant_dictionary_code UNIQUE(tenant_id,code),
 CONSTRAINT ck_tenant_dictionary_status CHECK(status IN('ENABLED','DISABLED')),
 CONSTRAINT ck_tenant_dictionary_sort CHECK(sort_order BETWEEN 0 AND 9999),
 INDEX ix_tenant_dictionary_list(tenant_id,deleted_at,sort_order)
);

-- parent_id 只表达同一字典内层级；跨字典关系由服务层和复合外键共同拒绝。
CREATE TABLE tenant_dictionary_item (
 id BINARY(16) NOT NULL PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 dictionary_id BINARY(16) NOT NULL,
 parent_id BINARY(16) NULL,
 code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 item_value VARCHAR(100) NOT NULL,
 name VARCHAR(100) NOT NULL,
 description VARCHAR(500) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 sort_order INT NOT NULL DEFAULT 100,
 version BIGINT NOT NULL DEFAULT 0,
 created_by BINARY(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_by BINARY(16) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted_by BINARY(16) NULL,
 deleted_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_dictionary_item_code UNIQUE(tenant_id,dictionary_id,code),
 CONSTRAINT uk_dictionary_item_value UNIQUE(tenant_id,dictionary_id,item_value),
 CONSTRAINT uk_dictionary_item_identity UNIQUE(tenant_id,dictionary_id,id),
 CONSTRAINT fk_dictionary_item_dictionary FOREIGN KEY(dictionary_id) REFERENCES tenant_dictionary(id),
 CONSTRAINT fk_dictionary_item_parent FOREIGN KEY(tenant_id,dictionary_id,parent_id) REFERENCES tenant_dictionary_item(tenant_id,dictionary_id,id),
 CONSTRAINT ck_dictionary_item_status CHECK(status IN('ENABLED','DISABLED')),
 CONSTRAINT ck_dictionary_item_sort CHECK(sort_order BETWEEN 0 AND 9999),
 INDEX ix_dictionary_item_list(tenant_id,dictionary_id,deleted_at,sort_order),
 INDEX ix_dictionary_item_parent(tenant_id,dictionary_id,parent_id,deleted_at)
);

-- 业务模块登记引用后，字典项只能停用而不能删除；reference_key 不保存敏感业务原值。
CREATE TABLE tenant_dictionary_item_reference (
 tenant_id BINARY(16) NOT NULL,
 item_id BINARY(16) NOT NULL,
 consumer VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reference_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,item_id,consumer,reference_key),
 CONSTRAINT fk_dictionary_reference_item FOREIGN KEY(item_id) REFERENCES tenant_dictionary_item(id),
 INDEX ix_dictionary_reference_item(tenant_id,item_id)
);

-- 审计为追加记录，只保存配置标识和动作，不记录凭据、Token 或业务敏感原值。
CREATE TABLE tenant_dictionary_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 dictionary_id BINARY(16) NOT NULL,
 item_id BINARY(16) NULL,
 actor_account_id BINARY(16) NOT NULL,
 action VARCHAR(32) NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_dictionary_audit_dictionary(tenant_id,dictionary_id,occurred_at)
);
