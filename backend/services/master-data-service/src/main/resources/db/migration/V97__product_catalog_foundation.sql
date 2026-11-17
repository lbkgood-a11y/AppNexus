-- 租户分类写入串行锚点，防止并发互移形成环及首个根节点写入竞态。
CREATE TABLE md_product_category_guard (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，同租户所有分类写事务竞争此行锁',
  created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
  PRIMARY KEY(tenant_id)
) COMMENT='分类聚合写事务锁锚点，不存其他业务模块数据';

-- 商品V1首个纵向切片：单位、分类及不可变空配置发布。
-- 不修改md_product，不切换旧商品写入口；后续属性/模板在新迁移中扩展。
CREATE TABLE md_product_unit (
  id BINARY(16) NOT NULL COMMENT '服务端生成单位UUID',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，所有关联包含此列',
  code VARCHAR(64) NOT NULL COMMENT '租户内唯一编码，1至64个ASCII字符，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '单位名称，必填，最多200字符',
  dimension_code VARCHAR(64) NOT NULL COMMENT '计量维度编码，必填，无默认值，不推导商品包装换算',
  precision_scale INT NOT NULL DEFAULT 0 COMMENT '显示精度，小数位0至12，默认0；不覆盖属性定义精度',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用，停用不删除历史单位',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，默认0，每次成功修改加1',
  created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
  PRIMARY KEY(id), UNIQUE KEY uk_pu_tenant_id(tenant_id,id),
  UNIQUE KEY uk_pu_code(tenant_id,code),
  CONSTRAINT ck_pu_precision CHECK(precision_scale BETWEEN 0 AND 12),
  CONSTRAINT ck_pu_version CHECK(version>=0)
) COMMENT='商品计量单位，不承载单位换算';

CREATE TABLE md_product_category (
  id BINARY(16) NOT NULL COMMENT '服务端生成分类UUID',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
  code VARCHAR(64) NOT NULL COMMENT '租户内唯一分类编码，创建后不可改',
  name VARCHAR(200) NOT NULL COMMENT '分类名称，必填，最多200字符',
  parent_id BINARY(16) NULL COMMENT '同租户父分类UUID，空表示根，根深度为1',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用；有启用后代时禁止停用，不级联',
  current_config_id BINARY(16) NULL COMMENT '当前已发布配置UUID；首次发布前为空',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '聚合乐观锁版本，默认0，分类及配置发布同一版本控制',
  created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
  PRIMARY KEY(id), UNIQUE KEY uk_pc_tenant_id(tenant_id,id),
  UNIQUE KEY uk_pc_code(tenant_id,code),
  KEY ix_pc_parent(tenant_id,parent_id,name,id),
  CONSTRAINT fk_pc_parent FOREIGN KEY(tenant_id,parent_id) REFERENCES md_product_category(tenant_id,id) ON DELETE RESTRICT,
  CONSTRAINT ck_pc_self CHECK(parent_id IS NULL OR parent_id<>id),
  CONSTRAINT ck_pc_version CHECK(version>=0)
) COMMENT='商品分类邻接树；服务端串行校验无环且最多8层';

CREATE TABLE md_product_category_config (
  id BINARY(16) NOT NULL COMMENT '已发布配置UUID，创建后正文不可变',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
  category_id BINARY(16) NOT NULL COMMENT '配置所属分类UUID，同租户约束',
  revision_no BIGINT NOT NULL COMMENT '同分类发布修订，从1递增，不与分类CAS版本混用',
  display_path_snapshot VARCHAR(2000) NOT NULL COMMENT '发布时分类路径快照，最多2000字符，不跟随移动改写',
  service_unit_required BOOLEAN NOT NULL DEFAULT FALSE COMMENT '服务商品单位是否必填，默认false；实物始终必填',
  published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC发布时间，微秒',
  created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
  PRIMARY KEY(id), UNIQUE KEY uk_pcc_tenant_id(tenant_id,id),
  UNIQUE KEY uk_pcc_owner(tenant_id,category_id,id),
  UNIQUE KEY uk_pcc_revision(tenant_id,category_id,revision_no),
  CONSTRAINT fk_pcc_category FOREIGN KEY(tenant_id,category_id) REFERENCES md_product_category(tenant_id,id) ON DELETE RESTRICT,
  CONSTRAINT ck_pcc_revision CHECK(revision_no>=1)
) COMMENT='不可变分类配置发布记录；首个切片仅允许四槽均空配置';

ALTER TABLE md_product_category ADD CONSTRAINT fk_pc_current_config
  FOREIGN KEY(tenant_id,id,current_config_id)
  REFERENCES md_product_category_config(tenant_id,category_id,id) ON DELETE RESTRICT;


-- V1权限与菜单；新配置维护权限不自动授予普通角色，租户管理员沿既有受控初始化规则获得。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000001'),'TENANT','masterdata.productunit:read','masterdata.productunit','read','查询商品计量单位','Read product units','仅当前租户商品配置读取；不授予商品、单位换算或下游业务权限。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000002'),'TENANT','masterdata.productunit:create','masterdata.productunit','create','新增商品计量单位','Create product units','仅当前租户商品配置维护；客户端不能指定租户或审计。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000003'),'TENANT','masterdata.productunit:update','masterdata.productunit','update','编辑商品计量单位','Update product units','仅修改名称等允许字段；维度和精度引用后受保护。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000004'),'TENANT','masterdata.productunit:set_status','masterdata.productunit','set_status','启停商品计量单位','Set product unit status','停用不删除历史引用。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000005'),'TENANT','masterdata.productcategory:read','masterdata.productcategory','read','查询商品分类','Read product categories','仅当前租户分类树及固定配置读取。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000006'),'TENANT','masterdata.productcategory:create','masterdata.productcategory','create','新增商品分类','Create product categories','分类编码租户内唯一，父级必须同租户。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000007'),'TENANT','masterdata.productcategory:update','masterdata.productcategory','update','编辑商品分类','Update product categories','树移动受深度、环和版本约束。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000008'),'TENANT','masterdata.productcategory:set_status','masterdata.productcategory','set_status','启停商品分类','Set product category status','停用不级联，启用后代存在时拒绝。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000009'),'TENANT','masterdata.productcategory:publish','masterdata.productcategory','publish','发布商品分类配置','Publish product category configuration','发布不可变四槽配置，当前切片只允许空槽。'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000010'),'TENANT','masterdata.productcategory:read_configuration','masterdata.productcategory','read_configuration','查看商品分类配置','Read product category configuration','只能读取本租户固定发布版本。');

INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
FROM access_role r JOIN access_permission p ON p.code IN ('masterdata.productunit:read','masterdata.productcategory:read','masterdata.productcategory:read_configuration')
WHERE r.system_key LIKE 'TENANT_ADMIN:%';
UPDATE access_role SET version=version+1 WHERE system_key LIKE 'TENANT_ADMIN:%';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type)
VALUES
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000101'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.product-category','商品分类','Product categories','/master-data/product-category','master-data/product-category','ri-node-tree', 'masterdata.productcategory:read',6,'TENANT'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000102'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.product-unit','计量单位','Product units','/master-data/product-unit','master-data/product-unit','ri-ruler-line', 'masterdata.productunit:read',7,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000101'),'masterdata.productcategory'),
(UUID_TO_BIN('019a65d0-0097-0000-8000-000000000102'),'masterdata.productunit');
