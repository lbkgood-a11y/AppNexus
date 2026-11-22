-- 业务工作区沿用现有在库货权读取准入，不向角色新增授权。
-- 系统初始化无可信操作人；审计时间由数据库按 UTC 生成。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0155-7000-8000-000000000001'),NULL,'MENU','builtin.navigation.warehousing.coil-title',
 '仓储货权与额度','Warehouse title and balances','/warehousing/coil-title','warehousing/coil-title',
 'ri/store-2-line','warehousing.coil.stockbalance:read',45,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6));
