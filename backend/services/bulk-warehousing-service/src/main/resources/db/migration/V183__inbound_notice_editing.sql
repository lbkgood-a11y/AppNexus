-- 编辑沿用原通知主单 version 字段，原表已有默认值0。
-- 编辑独立准入，系统迁移无可信账号；授权仍由统一平台维护。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0183-7000-8000-000000000001'),'TENANT','warehousing.inbound:update',
 'warehousing.inbound','update','编辑入仓通知','Edit inbound notice','同事务编辑主单和明细，检查原归属、实收事实及版本');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE code='warehousing.inbound:update' AND scope_type='TENANT';
