-- 系统迁移无可信账号时允许 NULL；保留原有账号和数据库生成时间语义。
ALTER TABLE tenant_parameter MODIFY updated_by BINARY(16) NULL COMMENT '最后修改实际账号 UUID；系统执行或历史未知为空，禁止客户端指定';
-- 高德安全代理使用固定同源入口；只更新旧路径，保持创建审计不变，系统迁移无实际账号。
UPDATE tenant_parameter SET parameter_value='/api/maps/_AMapService',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE parameter_key='map.amap-service-host' AND parameter_value<>'/api/maps/_AMapService';
