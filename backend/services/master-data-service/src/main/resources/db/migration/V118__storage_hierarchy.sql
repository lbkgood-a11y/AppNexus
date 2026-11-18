-- 仓库 → 库区 → 库位固定三层；父级使用同租户不可变编码，禁止跨租户引用。
CREATE TABLE md_storage_area (
 id BINARY(16) NOT NULL COMMENT '服务端生成档案UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，必填，由会话确定',
 code VARCHAR(64) NOT NULL COMMENT '本租户同类唯一编码，1至64字符，创建后不可修改',
 name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多200字符',
 parent_code VARCHAR(64) NOT NULL COMMENT '直属上级编码，必填，创建后不可迁移；复合外键限定当前租户',
 area_type VARCHAR(500) NOT NULL DEFAULT '' COMMENT '库区类型，缺省未指定；RECEIVING收货区、WORK作业区、GOOD良品区、DEFECTIVE次品区、PACKAGING包材区、VIRTUAL虚拟区、FINISHED成品区',
 capacity VARCHAR(500) NOT NULL DEFAULT '' COMMENT '库容，单位吨，缺省未知；非负十进制，最多18位整数和6位小数',
 remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注，缺省空，最多500字符',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用；停用前必须先停用所有启用下级，保留历史资料',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，初始0，每次成功修改或启停递增1',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无可信账号的系统执行允许NULL',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒精度',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无可信账号的系统执行允许NULL',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC最后修改时间，与业务同事务更新',
 PRIMARY KEY(id), UNIQUE KEY uk_storage_area_code(tenant_id,code),
 KEY ix_storage_area_parent(tenant_id,parent_code,enabled),
 CONSTRAINT fk_storage_area_parent FOREIGN KEY(tenant_id,parent_code) REFERENCES md_warehouse(tenant_id,code),
 CONSTRAINT ck_storage_area_version CHECK(version>=0)
) COMMENT='库区租户公共档案';
CREATE TABLE md_storage_location (
 id BINARY(16) NOT NULL COMMENT '服务端生成档案UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，必填，由会话确定',
 code VARCHAR(64) NOT NULL COMMENT '本租户同类唯一编码，1至64字符，创建后不可修改',
 name VARCHAR(200) NOT NULL COMMENT '业务名称，必填，最多200字符',
 parent_code VARCHAR(64) NOT NULL COMMENT '直属上级编码，必填，创建后不可迁移；复合外键限定当前租户',
 
 capacity VARCHAR(500) NOT NULL DEFAULT '' COMMENT '库容，单位吨，缺省未知；非负十进制，最多18位整数和6位小数',
 remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注，缺省空，最多500字符',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用；停用前必须先停用所有启用下级，保留历史资料',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，初始0，每次成功修改或启停递增1',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无可信账号的系统执行允许NULL',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒精度',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无可信账号的系统执行允许NULL',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC最后修改时间，与业务同事务更新',
 PRIMARY KEY(id), UNIQUE KEY uk_storage_location_code(tenant_id,code),
 KEY ix_storage_location_parent(tenant_id,parent_code,enabled),
 CONSTRAINT fk_storage_location_parent FOREIGN KEY(tenant_id,parent_code) REFERENCES md_storage_area(tenant_id,code),
 CONSTRAINT ck_storage_location_version CHECK(version>=0)
) COMMENT='库位租户公共档案';
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000001'),'TENANT','masterdata.storagearea:read','masterdata.storagearea','read','查询库区','read Storage areas','仅当前可信租户的库区；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000002'),'TENANT','masterdata.storagearea:create','masterdata.storagearea','create','新增库区','create Storage areas','仅当前可信租户的库区；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000003'),'TENANT','masterdata.storagearea:update','masterdata.storagearea','update','编辑库区','update Storage areas','仅当前可信租户的库区；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000004'),'TENANT','masterdata.storagearea:set_status','masterdata.storagearea','set_status','启停库区','set_status Storage areas','仅当前可信租户的库区；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000005'),'TENANT','masterdata.storagelocation:read','masterdata.storagelocation','read','查询库位','read Storage locations','仅当前可信租户的库位；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000006'),'TENANT','masterdata.storagelocation:create','masterdata.storagelocation','create','新增库位','create Storage locations','仅当前可信租户的库位；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000007'),'TENANT','masterdata.storagelocation:update','masterdata.storagelocation','update','编辑库位','update Storage locations','仅当前可信租户的库位；独立鉴权、父级约束与版本校验。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000008'),'TENANT','masterdata.storagelocation:set_status','masterdata.storagelocation','set_status','启停库位','set_status Storage locations','仅当前可信租户的库位；独立鉴权、父级约束与版本校验。');
-- 现有租户管理员同步独立权限；普通角色不自动扩权。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT r.id,p.id,'TENANT',(SELECT rp.granted_by FROM access_role_permission rp WHERE rp.role_id=r.id LIMIT 1) FROM access_role r JOIN access_permission p ON p.resource IN ('masterdata.storagearea','masterdata.storagelocation') WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id) SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id JOIN access_permission p ON p.resource IN ('masterdata.storagearea','masterdata.storagelocation') WHERE r.system_key LIKE 'TENANT_ADMIN:%';
-- 同一页面维护两个对象，仓库只作为已授权的直属父级选择。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000100'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.storage','库区与库位','Storage areas and locations','/master-data/storage','master-data/storage','ri/store-2-line','masterdata.storagearea:read',4,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000100'),'masterdata.storagearea'),(UUID_TO_BIN('019a65d0-0118-0000-8000-000000000100'),'masterdata.storagelocation');
