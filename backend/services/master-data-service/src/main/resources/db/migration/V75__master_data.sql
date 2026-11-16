-- 五类主数据独立实体表；编码仅在租户和同类内唯一，归属、启停和版本不由扩展字段覆盖。

CREATE TABLE md_person (
  id BINARY(16) NOT NULL COMMENT '档案 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，不允许客户端指定',
  code VARCHAR(64) NOT NULL COMMENT '租户内同类唯一编码，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多 200 字符',
  english_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '英文名称，最多 500 字符，缺省空',phone VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系电话文本，最多 500 字符，缺省空，不转换数字以保留区号',email VARCHAR(500) NOT NULL DEFAULT '' COMMENT '电子邮箱，最多 200 字符，缺省空，非空时校验邮箱格式',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '缺省启用；停用仅限制未来业务使用，不删除历史资料',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，缺省 0，每次修改递增 1',
  PRIMARY KEY(id), UNIQUE KEY uk_md_person_tenant_code(tenant_id,code),
  KEY ix_md_person_tenant_status(tenant_id,enabled)
) COMMENT='人员管理租户公共档案';

CREATE TABLE md_partner (
  id BINARY(16) NOT NULL COMMENT '档案 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，不允许客户端指定',
  code VARCHAR(64) NOT NULL COMMENT '租户内同类唯一编码，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多 200 字符',
  partner_type VARCHAR(500) NOT NULL DEFAULT '' COMMENT '客商类型，必填 CUSTOMER 客户、SUPPLIER 供应商或 BOTH 兼具；无业务默认值',credit_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT '统一社会信用代码，最多 500 字符，缺省空；不作为跨租户共享标识',contact_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '业务联系人，最多 500 字符，缺省空',phone VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系电话文本，最多 500 字符，缺省空，不转换数字以保留区号',address VARCHAR(500) NOT NULL DEFAULT '' COMMENT '业务地址，最多 500 字符，缺省空',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '缺省启用；停用仅限制未来业务使用，不删除历史资料',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，缺省 0，每次修改递增 1',
  PRIMARY KEY(id), UNIQUE KEY uk_md_partner_tenant_code(tenant_id,code),
  KEY ix_md_partner_tenant_status(tenant_id,enabled)
) COMMENT='客商管理租户公共档案';

CREATE TABLE md_warehouse (
  id BINARY(16) NOT NULL COMMENT '档案 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，不允许客户端指定',
  code VARCHAR(64) NOT NULL COMMENT '租户内同类唯一编码，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多 200 字符',
  address VARCHAR(500) NOT NULL DEFAULT '' COMMENT '业务地址，最多 500 字符，缺省空',contact_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '业务联系人，最多 500 字符，缺省空',phone VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系电话文本，最多 500 字符，缺省空，不转换数字以保留区号',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '缺省启用；停用仅限制未来业务使用，不删除历史资料',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，缺省 0，每次修改递增 1',
  PRIMARY KEY(id), UNIQUE KEY uk_md_warehouse_tenant_code(tenant_id,code),
  KEY ix_md_warehouse_tenant_status(tenant_id,enabled)
) COMMENT='仓库管理租户公共档案';

CREATE TABLE md_country_region (
  id BINARY(16) NOT NULL COMMENT '档案 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，不允许客户端指定',
  code VARCHAR(64) NOT NULL COMMENT '租户内同类唯一编码，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多 200 字符',
  english_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '英文名称，最多 500 字符，缺省空',alpha3_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'ISO 3166-1 三位大写字母代码，必填，无业务默认值',numeric_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'ISO 3166-1 三位数字代码，保留前导零，必填，无业务默认值',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '缺省启用；停用仅限制未来业务使用，不删除历史资料',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，缺省 0，每次修改递增 1',
  PRIMARY KEY(id), UNIQUE KEY uk_md_country_region_tenant_code(tenant_id,code),
  KEY ix_md_country_region_tenant_status(tenant_id,enabled)
) COMMENT='国别地区租户公共档案';

CREATE TABLE md_product (
  id BINARY(16) NOT NULL COMMENT '档案 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，不允许客户端指定',
  code VARCHAR(64) NOT NULL COMMENT '租户内同类唯一编码，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多 200 字符',
  specification VARCHAR(500) NOT NULL DEFAULT '' COMMENT '规格型号，最多 500 字符，缺省空',unit VARCHAR(500) NOT NULL DEFAULT '' COMMENT '基本单位文本，必填，1–32 字符，不进行数量换算，无业务默认值',barcode VARCHAR(500) NOT NULL DEFAULT '' COMMENT '商品条码文本，最多 500 字符，缺省空，保留前导零',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '缺省启用；停用仅限制未来业务使用，不删除历史资料',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，缺省 0，每次修改递增 1',
  PRIMARY KEY(id), UNIQUE KEY uk_md_product_tenant_code(tenant_id,code),
  KEY ix_md_product_tenant_status(tenant_id,enabled)
) COMMENT='商品管理租户公共档案';

-- 新权限只补给系统租户管理员，普通角色必须显式授权，不从无关权限推导。

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000001'),'TENANT','masterdata.person:read','masterdata.person','read','查询人员管理','read Personnel','仅当前可信租户的人员管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000002'),'TENANT','masterdata.person:create','masterdata.person','create','新增人员管理','create Personnel','仅当前可信租户的人员管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000003'),'TENANT','masterdata.person:update','masterdata.person','update','编辑人员管理','update Personnel','仅当前可信租户的人员管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000004'),'TENANT','masterdata.person:set_status','masterdata.person','set_status','启停人员管理','set_status Personnel','仅当前可信租户的人员管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000005'),'TENANT','masterdata.partner:read','masterdata.partner','read','查询客商管理','read Business partners','仅当前可信租户的客商管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000006'),'TENANT','masterdata.partner:create','masterdata.partner','create','新增客商管理','create Business partners','仅当前可信租户的客商管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000007'),'TENANT','masterdata.partner:update','masterdata.partner','update','编辑客商管理','update Business partners','仅当前可信租户的客商管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000008'),'TENANT','masterdata.partner:set_status','masterdata.partner','set_status','启停客商管理','set_status Business partners','仅当前可信租户的客商管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000009'),'TENANT','masterdata.warehouse:read','masterdata.warehouse','read','查询仓库管理','read Warehouses','仅当前可信租户的仓库管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000010'),'TENANT','masterdata.warehouse:create','masterdata.warehouse','create','新增仓库管理','create Warehouses','仅当前可信租户的仓库管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000011'),'TENANT','masterdata.warehouse:update','masterdata.warehouse','update','编辑仓库管理','update Warehouses','仅当前可信租户的仓库管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000012'),'TENANT','masterdata.warehouse:set_status','masterdata.warehouse','set_status','启停仓库管理','set_status Warehouses','仅当前可信租户的仓库管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000013'),'TENANT','masterdata.countryregion:read','masterdata.countryregion','read','查询国别地区','read Countries and regions','仅当前可信租户的国别地区；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000014'),'TENANT','masterdata.countryregion:create','masterdata.countryregion','create','新增国别地区','create Countries and regions','仅当前可信租户的国别地区；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000015'),'TENANT','masterdata.countryregion:update','masterdata.countryregion','update','编辑国别地区','update Countries and regions','仅当前可信租户的国别地区；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000016'),'TENANT','masterdata.countryregion:set_status','masterdata.countryregion','set_status','启停国别地区','set_status Countries and regions','仅当前可信租户的国别地区；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000017'),'TENANT','masterdata.product:read','masterdata.product','read','查询商品管理','read Products','仅当前可信租户的商品管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000018'),'TENANT','masterdata.product:create','masterdata.product','create','新增商品管理','create Products','仅当前可信租户的商品管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000019'),'TENANT','masterdata.product:update','masterdata.product','update','编辑商品管理','update Products','仅当前可信租户的商品管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000020'),'TENANT','masterdata.product:set_status','masterdata.product','set_status','启停商品管理','set_status Products','仅当前可信租户的商品管理；不授予账号、组织、库存、价格或授权管理能力。');

INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000) FROM access_role r JOIN access_permission p ON p.resource IN ('masterdata.person','masterdata.partner','masterdata.warehouse','masterdata.countryregion','masterdata.product') WHERE r.system_key LIKE 'TENANT_ADMIN:%';

INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id) SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id JOIN access_permission p ON p.resource IN ('masterdata.person','masterdata.partner','masterdata.warehouse','masterdata.countryregion','masterdata.product') WHERE r.system_key LIKE 'TENANT_ADMIN:%';

UPDATE access_role SET version=version+1 WHERE system_key LIKE 'TENANT_ADMIN:%';

-- 导航仅绑定真实构建页面；菜单可见性不替代服务端授权。

INSERT INTO platform_menu(id,type,code,title_zh,title_en,icon,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'DIRECTORY','builtin.navigation.master-data','主数据管理','Master data','ri/database-2-line',12,'TENANT');

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000101'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.person','人员管理','Personnel','/master-data/person','master-data/person','ri/database-2-line','masterdata.person:read',1,'TENANT');

INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000101'),'masterdata.person');

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000102'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.partner','客商管理','Business partners','/master-data/partner','master-data/partner','ri/database-2-line','masterdata.partner:read',2,'TENANT');

INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000102'),'masterdata.partner');

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000103'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.warehouse','仓库管理','Warehouses','/master-data/warehouse','master-data/warehouse','ri/database-2-line','masterdata.warehouse:read',3,'TENANT');

INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000103'),'masterdata.warehouse');

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000104'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.countryregion','国别地区','Countries and regions','/master-data/country-region','master-data/country-region','ri/database-2-line','masterdata.countryregion:read',4,'TENANT');

INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000104'),'masterdata.countryregion');

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000105'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.product','商品管理','Products','/master-data/product','master-data/product','ri/database-2-line','masterdata.product:read',5,'TENANT');

INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0075-7000-8000-000000000105'),'masterdata.product');
