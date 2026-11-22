-- 查看冻结与解除凭证使用独立的租户读取权限，沿用现有监管数据资源。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0158-0000-8000-000000000001'),'TENANT','warehousing.stock.restriction:read',
  'warehousing.stock.restriction','read','查看库存监管记录','View stock restrictions','按已授权库存身份查看冻结及解除凭证');

-- 继续使用原监管资源的 ALL 数据范围，实际查询还须校验当前仓库、库位与货主。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
VALUES(UUID_TO_BIN('019a65d0-0158-0000-8000-000000000001'),'ALL','warehousing.stock.restriction');
