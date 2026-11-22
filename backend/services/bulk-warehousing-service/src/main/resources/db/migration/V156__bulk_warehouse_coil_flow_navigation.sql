-- 复用在库货权读取准入；实际进仓、过户、出仓与返仓命令各走原操作，不自动补授权限。
-- 系统初始化无可信操作人，审计时间由数据库按 UTC 生成。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0156-7000-8000-000000000001'),NULL,'MENU','builtin.navigation.warehousing.coil-flow',
 '整卷履约','Coil fulfillment','/warehousing/coil-flow','warehousing/coil-flow',
 'ri/store-2-line','warehousing.coil.stockbalance:read',46,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6));
