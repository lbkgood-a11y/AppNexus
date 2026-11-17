-- 商品V1已实现的草稿管理权限；普通角色不自动获得，新接口仍按可信上下文鉴权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
 VALUES(UUID_TO_BIN('019a65d0-0104-0000-8000-000000000001'),'TENANT','masterdata.product:manage_draft','masterdata.product','manage_draft','归档恢复商品草稿','Manage product drafts','仅当前租户DRAFT归档和恢复；不授予删除、编码回收、编辑或启用权限。');
