-- 已冲红库存保留原实收量和位置，查询以 UTC 冲红时间区分有效库存和历史。
ALTER TABLE bw_stock_receipt ADD COLUMN reversed_at TIMESTAMP(6) NULL
  COMMENT '已完成整笔冲红的数据库 UTC 时点，历史及未冲红库存为空';

-- 冲红采用独立授权，不继承过账或草稿操作权限。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0193-7000-8000-000000000001'),'TENANT','warehousing.receipt:reverse',
 'warehousing.receipt','reverse','冲红入库单','Reverse inbound receipt',
 '冲红尚未发生后续业务的入库原单并退回通知累计量');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.receipt:reverse';
