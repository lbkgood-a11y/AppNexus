-- 字典操作为租户范围功能，数据始终限定当前可信租户；不隐式授予组织 GRANT。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000001'),'TENANT','configuration.dictionary:read','configuration.dictionary','read','查看字典及字典项','Read dictionaries and items','当前租户业务字典操作，不包含授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000002'),'TENANT','configuration.dictionary:create','configuration.dictionary','create','创建字典','Create dictionaries','当前租户业务字典操作，不包含授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000003'),'TENANT','configuration.dictionary:update','configuration.dictionary','update','修改与启停字典','Update dictionaries','当前租户业务字典操作，不包含授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000004'),'TENANT','configuration.dictionary:delete','configuration.dictionary','delete','删除字典','Delete dictionaries','当前租户业务字典操作，不包含授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000005'),'TENANT','configuration.dictionary:item_create','configuration.dictionary','item_create','创建字典项','Create dictionary items','当前租户业务字典操作，不包含授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000006'),'TENANT','configuration.dictionary:item_update','configuration.dictionary','item_update','修改与启停字典项','Update dictionary items','当前租户业务字典操作，不包含授权管理能力。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0028-7000-8000-000000000007'),'TENANT','configuration.dictionary:item_delete','configuration.dictionary','item_delete','删除字典项','Delete dictionary items','当前租户业务字典操作，不包含授权管理能力。');

-- 已有系统租户管理员保持原字典管理能力；普通角色与历史自定义授权不自动扩权。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
 SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
 FROM access_role r JOIN access_permission p ON p.resource='configuration.dictionary'
 WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id)
 SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id
 JOIN access_permission p ON p.resource='configuration.dictionary' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
-- 推进受影响成员版本，使已有展示状态识别授权扩展；不刷新已有分支到期时间。
UPDATE tenant_member m SET authority_version=authority_version+1 WHERE EXISTS(SELECT 1 FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id WHERE g.tenant_id=m.tenant_id AND g.member_id=m.id AND r.system_key LIKE 'TENANT_ADMIN:%');
UPDATE platform_menu SET permission_code='configuration.dictionary:read',version=version+1 WHERE component_key='configuration/dictionaries' AND scope_type='TENANT';
