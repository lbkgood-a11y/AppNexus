-- 行政区划复用现有主数据鉴权、版本与审计；不改写已有国别编码或伪造历史审计。
CREATE TABLE md_subdivision (
  id BINARY(16) NOT NULL COMMENT '档案 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，不允许客户端指定',
  code VARCHAR(64) NOT NULL COMMENT '租户内同类唯一编码，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多 200 字符',
  country_code VARCHAR(2) NOT NULL COMMENT 'ISO 3166-1 二字码，由标准区划编码确定，必填',
  subdivision_type VARCHAR(100) NOT NULL COMMENT '标准区划类型，来自 ISO 快照，不假设各国采用省市县层级',
  parent_code VARCHAR(16) NOT NULL DEFAULT '' COMMENT 'ISO 3166-2 上级区划完整编码；空表示标准未提供上级',
  created_by BINARY(16) NULL COMMENT '可信创建账号；系统执行或历史未知为 NULL，客户端不可指定',
  created_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间，微秒精度；创建后不变',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号；系统执行或历史未知为 NULL',
  updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间，微秒精度；同业务事务更新',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '缺省启用；停用仅限制未来业务使用，不删除历史资料',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，缺省 0，每次修改递增 1',
  PRIMARY KEY(id), UNIQUE KEY uk_md_subdivision_tenant_code(tenant_id,code),
  KEY ix_md_subdivision_tenant_status(tenant_id,enabled),
  KEY ix_md_subdivision_country(tenant_id,country_code,enabled),
  KEY ix_md_subdivision_parent(tenant_id,parent_code)
) COMMENT='ISO 3166-2 行政区划租户公共档案';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0092-7000-8000-000000000013'),'TENANT','masterdata.subdivision:read','masterdata.subdivision','read','查询行政区划','read Subdivisions','仅当前可信租户的行政区划；不授予账号、组织、库存、价格或授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0092-7000-8000-000000000014'),'TENANT','masterdata.subdivision:create','masterdata.subdivision','create','新增行政区划','create Subdivisions','仅当前可信租户的行政区划；不授予账号、组织、库存、价格或授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0092-7000-8000-000000000015'),'TENANT','masterdata.subdivision:update','masterdata.subdivision','update','编辑行政区划','update Subdivisions','仅当前可信租户的行政区划；不授予账号、组织、库存、价格或授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0092-7000-8000-000000000016'),'TENANT','masterdata.subdivision:set_status','masterdata.subdivision','set_status','启停行政区划','set_status Subdivisions','仅当前可信租户的行政区划；不授予账号、组织、库存、价格或授权管理能力。');
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000) FROM access_role r JOIN access_permission p ON p.resource IN ('masterdata.subdivision') WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id) SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id JOIN access_permission p ON p.resource IN ('masterdata.subdivision') WHERE r.system_key LIKE 'TENANT_ADMIN:%';
UPDATE access_role SET version=version+1 WHERE system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0092-7000-8000-000000000104'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.subdivision','行政区划','Subdivisions','/master-data/subdivision','master-data/subdivision','ri/database-2-line','masterdata.subdivision:read',4,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0092-7000-8000-000000000104'),'masterdata.subdivision');
