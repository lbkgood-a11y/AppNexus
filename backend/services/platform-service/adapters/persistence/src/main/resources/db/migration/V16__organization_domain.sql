-- 组织类型决定独立树边界；BUSINESS 是存量平面组织迁移及未指定类型时的默认类型。
CREATE TABLE tenant_organization_type (
 tenant_id BINARY(16) NOT NULL,
 id BINARY(16) NOT NULL,
 code VARCHAR(50) NOT NULL,
 name VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY (tenant_id,id),
 CONSTRAINT uk_org_type_code UNIQUE (tenant_id,code),
 CONSTRAINT uk_org_type_name UNIQUE (tenant_id,name)
);

-- 为每个已有租户建立默认业务组织类型，保证存量组织迁移后仍有明确树边界。
INSERT INTO tenant_organization_type(tenant_id,id,code,name)
SELECT DISTINCT tenant_id,UUID_TO_BIN(UUID()),'BUSINESS','业务组织' FROM tenant_organization_unit;

-- 组织编码创建后稳定；parent_id 只表达同类型树内上级，跨树归属使用关系表。
ALTER TABLE tenant_organization_unit
 ADD COLUMN code VARCHAR(50) NULL AFTER id,
 ADD COLUMN type_id BINARY(16) NULL AFTER code,
 ADD COLUMN parent_id BINARY(16) NULL AFTER type_id,
 ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER name,
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER enabled;

UPDATE tenant_organization_unit unit
JOIN tenant_organization_type type ON type.tenant_id=unit.tenant_id AND type.code='BUSINESS'
SET unit.code=CONCAT('ORG-',LEFT(REPLACE(BIN_TO_UUID(unit.id),'-',''),12)),unit.type_id=type.id;

ALTER TABLE tenant_organization_unit
 MODIFY code VARCHAR(50) NOT NULL,
 MODIFY type_id BINARY(16) NOT NULL,
 ADD CONSTRAINT uk_org_unit_code UNIQUE(tenant_id,code),
 ADD CONSTRAINT fk_org_unit_type FOREIGN KEY(tenant_id,type_id) REFERENCES tenant_organization_type(tenant_id,id),
 ADD CONSTRAINT fk_org_unit_parent FOREIGN KEY(tenant_id,parent_id) REFERENCES tenant_organization_unit(tenant_id,id),
 ADD INDEX ix_org_unit_tree(tenant_id,type_id,parent_id);

-- 组织关系连接不同或相同类型树，relation_type 表达业务归属、财务归属等稳定语义编码。
CREATE TABLE tenant_organization_relation (
 tenant_id BINARY(16) NOT NULL,
 id BINARY(16) NOT NULL,
 relation_type VARCHAR(50) NOT NULL,
 source_id BINARY(16) NOT NULL,
 target_id BINARY(16) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id),
 CONSTRAINT uk_org_relation UNIQUE(tenant_id,relation_type,source_id,target_id),
 CONSTRAINT fk_org_relation_source FOREIGN KEY(tenant_id,source_id) REFERENCES tenant_organization_unit(tenant_id,id),
 CONSTRAINT fk_org_relation_target FOREIGN KEY(tenant_id,target_id) REFERENCES tenant_organization_unit(tenant_id,id)
);

-- 岗位属于单一组织；停用岗位不会删除历史任职。
CREATE TABLE tenant_organization_position (
 tenant_id BINARY(16) NOT NULL,
 id BINARY(16) NOT NULL,
 organization_id BINARY(16) NOT NULL,
 code VARCHAR(50) NOT NULL,
 name VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id),
 CONSTRAINT uk_org_position_code UNIQUE(tenant_id,code),
 CONSTRAINT fk_org_position_unit FOREIGN KEY(tenant_id,organization_id) REFERENCES tenant_organization_unit(tenant_id,id)
);

-- 任职关联有效租户成员与岗位；结束时间为空表示持续有效，时间统一保存为 UTC 时间点。
ALTER TABLE tenant_member ADD CONSTRAINT uk_tenant_member_identity UNIQUE(tenant_id,id);

CREATE TABLE tenant_organization_assignment (
 tenant_id BINARY(16) NOT NULL,
 id BINARY(16) NOT NULL,
 member_id BINARY(16) NOT NULL,
 position_id BINARY(16) NOT NULL,
 starts_at TIMESTAMP(6) NOT NULL,
 ends_at TIMESTAMP(6) NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id),
 CONSTRAINT uk_org_assignment UNIQUE(tenant_id,member_id,position_id,starts_at),
 CONSTRAINT fk_org_assignment_member FOREIGN KEY(tenant_id,member_id) REFERENCES tenant_member(tenant_id,id),
 CONSTRAINT fk_org_assignment_position FOREIGN KEY(tenant_id,position_id) REFERENCES tenant_organization_position(tenant_id,id)
);

-- 负责人必须来自本组织岗位上的有效任职；外键保证负责人引用的是同租户任职记录。
CREATE TABLE tenant_organization_leader (
 tenant_id BINARY(16) NOT NULL,
 organization_id BINARY(16) NOT NULL,
 assignment_id BINARY(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,organization_id,assignment_id),
 CONSTRAINT fk_org_leader_unit FOREIGN KEY(tenant_id,organization_id) REFERENCES tenant_organization_unit(tenant_id,id),
 CONSTRAINT fk_org_leader_assignment FOREIGN KEY(tenant_id,assignment_id) REFERENCES tenant_organization_assignment(tenant_id,id)
);
