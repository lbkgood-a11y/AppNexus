-- 账号成员只承载租户访问；人员承载组织归属及岗位。旧表保留为只读迁移凭证，运行时不回退查询。
-- 每个尚无人员身份的存量成员生成独立人员；稳定标识与来源表保证迁移可核对，不按姓名猜测合并。
CREATE TABLE md_person_member_migration (
 tenant_id BINARY(16) NOT NULL COMMENT '存量关系的租户，必填',
 member_id BINARY(16) NOT NULL COMMENT '迁移来源租户成员 UUID，必填',
 person_id BINARY(16) NOT NULL COMMENT '独立生成的人员 UUID，不复用账号或成员 UUID',
 PRIMARY KEY(tenant_id,member_id),UNIQUE KEY uk_member_person_migration(tenant_id,person_id)
) COMMENT='一次性账号成员到人员迁移映射，只用于对账';
INSERT INTO md_person_member_migration(tenant_id,member_id,person_id)
SELECT m.tenant_id,m.id,UUID_TO_BIN(UUID()) FROM tenant_member m
WHERE NOT EXISTS(SELECT 1 FROM md_person_user_assignment a WHERE a.tenant_id=m.tenant_id AND a.member_id=m.id);
INSERT INTO md_person(id,tenant_id,code,name,enabled,employment_status)
SELECT migration_link.person_id,m.tenant_id,CONCAT('MIG-',REPLACE(BIN_TO_UUID(m.id),'-','')),a.display_name,m.enabled,'ACTIVE'
FROM md_person_member_migration migration_link JOIN tenant_member m ON m.tenant_id=migration_link.tenant_id AND m.id=migration_link.member_id
JOIN identity_account a ON a.id=m.account_id;
-- 新关系不含组织；旧组织限定关系拆分为“账号人员关联”和“人员组织归属”，同一人员用户只保留一条。
CREATE TABLE md_person_account (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户，必填',id BINARY(16) NOT NULL COMMENT '关系 UUID，服务端生成',
 person_id BINARY(16) NOT NULL COMMENT '同租户人员，必填',member_id BINARY(16) NOT NULL COMMENT '同租户账号成员，必填',
 created_by BINARY(16) NULL COMMENT '实际关联账号；迁移未知为 NULL',created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '关联建立时间，UTC，缺省当前时间',
 PRIMARY KEY(tenant_id,id),UNIQUE KEY uk_person_account(tenant_id,person_id,member_id),KEY ix_account_person(tenant_id,member_id),
 FOREIGN KEY(tenant_id,person_id) REFERENCES md_person(tenant_id,id)
) COMMENT='账号成员与人员多对多关系，不授予业务权限';
INSERT INTO md_person_account(tenant_id,id,person_id,member_id,created_by,created_at)
SELECT tenant_id,UUID_TO_BIN(UUID()),person_id,member_id,MIN(created_by),MIN(created_at) FROM md_person_user_assignment GROUP BY tenant_id,person_id,member_id;
INSERT INTO md_person_account(tenant_id,id,person_id,member_id)
SELECT tenant_id,UUID_TO_BIN(UUID()),person_id,member_id FROM md_person_member_migration;
CREATE TABLE tenant_person_organization (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户，必填',id BINARY(16) NOT NULL COMMENT '归属关系 UUID',
 person_id BINARY(16) NOT NULL COMMENT '同租户人员 UUID',organization_id BINARY(16) NOT NULL COMMENT '同租户组织 UUID',
 is_primary BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否主组织，默认 false；同人员最多一个主组织由事务约束',
 starts_on DATE NULL COMMENT '生效业务日期，缺省无起始限制',ends_on DATE NULL COMMENT '结束业务日期，缺省无限期；包含结束当天',
 remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '归属备注，最多 500 字符，缺省空',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '归属启用，默认 true',version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，默认 0，修改递增',
 PRIMARY KEY(tenant_id,id),UNIQUE KEY uk_person_organization(tenant_id,person_id,organization_id),
 FOREIGN KEY(tenant_id,person_id) REFERENCES md_person(tenant_id,id),FOREIGN KEY(tenant_id,organization_id) REFERENCES tenant_organization_unit(tenant_id,id)
) COMMENT='人员组织归属唯一权威，不直接关联账号';
INSERT INTO tenant_person_organization(tenant_id,id,person_id,organization_id)
SELECT DISTINCT tenant_id,UUID_TO_BIN(UUID()),person_id,organization_id FROM (SELECT DISTINCT tenant_id,person_id,organization_id FROM md_person_user_assignment) old_links;
-- 原成员主组织转到其所有关联人员；不改变既有人员关系，不把多身份权限合并。
INSERT IGNORE INTO tenant_person_organization(tenant_id,id,person_id,organization_id,is_primary)
SELECT a.tenant_id,UUID_TO_BIN(UUID()),a.person_id,p.organization_id,TRUE FROM md_person_account a
JOIN tenant_member_primary_organization p ON p.tenant_id=a.tenant_id AND p.member_id=a.member_id;
UPDATE tenant_person_organization po JOIN md_person_account a ON a.tenant_id=po.tenant_id AND a.person_id=po.person_id JOIN tenant_member_primary_organization old_po ON old_po.tenant_id=a.tenant_id AND old_po.member_id=a.member_id AND old_po.organization_id=po.organization_id SET po.is_primary=TRUE;
-- 主组织最多一个；多账号来源冲突时确定性保留最小组织标识，其余为普通归属。
UPDATE tenant_person_organization p JOIN (SELECT tenant_id,person_id,MIN(organization_id) main_id FROM tenant_person_organization WHERE is_primary=TRUE GROUP BY tenant_id,person_id) choice ON choice.tenant_id=p.tenant_id AND choice.person_id=p.person_id SET p.is_primary=(p.organization_id=choice.main_id);
CREATE TABLE tenant_person_position (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户',id BINARY(16) NOT NULL COMMENT '任职 UUID',person_id BINARY(16) NOT NULL COMMENT '人员 UUID，不能使用租户成员 UUID',
 position_id BINARY(16) NOT NULL COMMENT '组织下岗位 UUID',starts_at TIMESTAMP(6) NOT NULL COMMENT '开始时间 UTC，必填',ends_at TIMESTAMP(6) NULL COMMENT '结束时间 UTC，缺省无限期',
 remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '任职备注，最多 500 字符，缺省空',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用',version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本默认 0',
 PRIMARY KEY(tenant_id,id),UNIQUE KEY uk_person_position(tenant_id,person_id,position_id,starts_at),
 FOREIGN KEY(tenant_id,person_id) REFERENCES md_person(tenant_id,id),FOREIGN KEY(tenant_id,position_id) REFERENCES tenant_organization_position(tenant_id,id)
) COMMENT='人员岗位任职唯一权威';
CREATE TABLE md_person_assignment_migration (
 tenant_id BINARY(16) NOT NULL COMMENT '来源租户',old_assignment_id BINARY(16) NOT NULL COMMENT '旧用户任职 UUID',person_id BINARY(16) NOT NULL COMMENT '目标人员 UUID',assignment_id BINARY(16) NOT NULL COMMENT '新人员任职 UUID',
 PRIMARY KEY(tenant_id,old_assignment_id,person_id)
) COMMENT='任职和负责人迁移对账';
INSERT INTO md_person_assignment_migration SELECT a.tenant_id,a.id,pa.person_id,UUID_TO_BIN(UUID()) FROM tenant_organization_assignment a JOIN md_person_account pa ON pa.tenant_id=a.tenant_id AND pa.member_id=a.member_id;
INSERT IGNORE INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at,ends_at,enabled,version)
SELECT a.tenant_id,migration_link.assignment_id,migration_link.person_id,a.position_id,a.starts_at,a.ends_at,a.enabled,a.version FROM tenant_organization_assignment a JOIN md_person_assignment_migration migration_link ON migration_link.tenant_id=a.tenant_id AND migration_link.old_assignment_id=a.id;
CREATE TABLE tenant_person_leader (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户',organization_id BINARY(16) NOT NULL COMMENT '组织 UUID',assignment_id BINARY(16) NOT NULL COMMENT '人员任职 UUID',
 PRIMARY KEY(tenant_id,organization_id,assignment_id),FOREIGN KEY(tenant_id,assignment_id) REFERENCES tenant_person_position(tenant_id,id)
) COMMENT='组织负责人来自人员有效任职';
INSERT IGNORE INTO tenant_person_leader SELECT l.tenant_id,l.organization_id,p.id FROM tenant_organization_leader l JOIN md_person_assignment_migration migration_link ON migration_link.tenant_id=l.tenant_id AND migration_link.old_assignment_id=l.assignment_id JOIN tenant_organization_assignment old_a ON old_a.tenant_id=l.tenant_id AND old_a.id=l.assignment_id JOIN tenant_person_position p ON p.tenant_id=migration_link.tenant_id AND p.person_id=migration_link.person_id AND p.position_id=old_a.position_id AND p.starts_at=old_a.starts_at;
-- 既有人员页组织/岗位明细归并到权威关系；非组织类明细保留原结构。
INSERT IGNORE INTO tenant_person_organization(tenant_id,id,person_id,organization_id,starts_on,ends_on,remarks)
SELECT d.tenant_id,d.id,d.person_id,u.id,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(content,'$.startsOn')),''),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(content,'$.endsOn')),''),COALESCE(JSON_UNQUOTE(JSON_EXTRACT(content,'$.remarks')),'') FROM md_person_detail d JOIN tenant_organization_unit u ON u.tenant_id=d.tenant_id AND u.id=UUID_TO_BIN(JSON_UNQUOTE(JSON_EXTRACT(content,'$.organizationId'))) WHERE d.section_code='organizations';
INSERT IGNORE INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at,ends_at,remarks)
SELECT d.tenant_id,d.id,d.person_id,p.id,COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(content,'$.startsOn')),''),'1970-01-02'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(content,'$.endsOn')),''),COALESCE(JSON_UNQUOTE(JSON_EXTRACT(content,'$.remarks')),'') FROM md_person_detail d JOIN tenant_organization_position p ON p.tenant_id=d.tenant_id AND p.id=UUID_TO_BIN(JSON_UNQUOTE(JSON_EXTRACT(content,'$.positionId'))) WHERE d.section_code='positions';
-- 不再将用户组织归属 API 作为有效运行协议；历史审计保留，旧关系表只作迁移依据。
