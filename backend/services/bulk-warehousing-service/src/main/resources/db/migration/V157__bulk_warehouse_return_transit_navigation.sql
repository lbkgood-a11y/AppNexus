-- 返仓在途工作区沿用现行清单读取权限，写入仍逐项执行既有准入。
-- 系统初始化无可信操作人，审计时间由数据库按 UTC 生成。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0157-7000-8000-000000000001'),NULL,'MENU','builtin.navigation.warehousing.return-transit',
 '跨仓返仓在途','Cross-warehouse return transit','/warehousing/return-transit','warehousing/return-transit',
 'ri/store-2-line','warehousing.outbound.coil:read_return_transit',47,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6));
