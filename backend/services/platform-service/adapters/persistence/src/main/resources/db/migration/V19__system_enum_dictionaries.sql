-- 系统当前使用的业务枚举统一登记为租户字典；稳定值保持与既有数据兼容，不迁移技术协议常量。
INSERT INTO tenant_dictionary(id,tenant_id,code,name,description,status,sort_order,created_by,updated_by)
SELECT UNHEX(MD5(CONCAT(HEX(t.id),':',d.code))),t.id,d.code,d.name,'系统预置业务枚举；值不可修改，可调整名称和停用状态。','ENABLED',d.sort_order,0x00000000000000000000000000000000,0x00000000000000000000000000000000
FROM platform_tenant t JOIN (
 SELECT 'common_status' code,'通用状态' name,10 sort_order UNION ALL
 SELECT 'menu_type','菜单类型',20 UNION ALL
 SELECT 'role_scope_type','角色管理域',30 UNION ALL
 SELECT 'role_type','角色类型',40 UNION ALL
 SELECT 'organization_relation_type','组织关系类型',50 UNION ALL
 SELECT 'organization_capability','组织操作能力',60
) d;

INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,status,sort_order,created_by,updated_by)
SELECT UNHEX(MD5(CONCAT(HEX(t.id),':',v.dictionary_code,':',v.item_value))),t.id,
 UNHEX(MD5(CONCAT(HEX(t.id),':',v.dictionary_code))),LOWER(v.item_value),v.item_value,v.item_name,'ENABLED',v.sort_order,
 0x00000000000000000000000000000000,0x00000000000000000000000000000000
FROM platform_tenant t JOIN (
 SELECT 'common_status' dictionary_code,'ENABLED' item_value,'启用' item_name,10 sort_order UNION ALL SELECT 'common_status','DISABLED','停用',20 UNION ALL
 SELECT 'menu_type','DIRECTORY','目录',10 UNION ALL SELECT 'menu_type','MENU','菜单',20 UNION ALL SELECT 'menu_type','BUTTON','按钮',30 UNION ALL
 SELECT 'role_scope_type','PLATFORM','平台',10 UNION ALL SELECT 'role_scope_type','TENANT','租户',20 UNION ALL
 SELECT 'role_type','SYSTEM','系统角色',10 UNION ALL SELECT 'role_type','CUSTOM','自定义角色',20 UNION ALL
 SELECT 'organization_relation_type','BUSINESS_OWNERSHIP','业务归属',10 UNION ALL SELECT 'organization_relation_type','FINANCIAL_OWNERSHIP','财务归属',20 UNION ALL
 SELECT 'organization_capability','READ','查看',10 UNION ALL SELECT 'organization_capability','CREATE','创建',20 UNION ALL SELECT 'organization_capability','UPDATE','修改',30 UNION ALL SELECT 'organization_capability','DELETE','删除',40 UNION ALL SELECT 'organization_capability','GRANT','授权管理',50
) v;
