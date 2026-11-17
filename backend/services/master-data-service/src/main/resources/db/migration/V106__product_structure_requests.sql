-- 商品结构迁移的成功幂等结果；请求记录与商品/值/历史在同一事务提交，不存预览Token。
CREATE TABLE md_product_structure_request (
 id BINARY(16) NOT NULL COMMENT '请求记录UUID，服务端生成',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 account_id BINARY(16) NOT NULL COMMENT '可信实际请求账号UUID，不允许客户端指定',
 product_id BINARY(16) NOT NULL COMMENT '商品UUID，与租户一起绑定',
 operation_code VARCHAR(32) NOT NULL DEFAULT 'migrateStructure' COMMENT '固定操作域migrateStructure，不是客户端执行器',
 request_key BINARY(16) NOT NULL COMMENT 'Idempotency-Key的UUID；仅同租户/账号/商品/操作唯一',
 payload_digest BINARY(32) NOT NULL COMMENT '规范化源版本/目标/处置载荷SHA-256摘要，不包含签名Token',
 result_product_version BIGINT NOT NULL COMMENT '成功后的商品CAS版本，重放不推进',
 result_structure_revision BIGINT NOT NULL COMMENT '成功后的结构修订号',
 change_id BINARY(16) NOT NULL COMMENT '同事务追加变更事件UUID',
 result_json JSON NOT NULL COMMENT '最小成功结果，不缓存敏感商品全量详情',
 completed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC完成时间',
 expires_at TIMESTAMP(6) NOT NULL COMMENT '7天重放窗口结束UTC时间；无自动删除任务',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空，禁止客户端指定',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空，禁止客户端指定',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间，与正文同事务',
 PRIMARY KEY(id),
 UNIQUE KEY uk_psr_key(tenant_id,account_id,product_id,operation_code,request_key),
 KEY ix_psr_expiry(expires_at),
 CONSTRAINT fk_psr_product FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id) ON DELETE RESTRICT,
 CHECK(operation_code='migrateStructure'),CHECK(result_product_version>=1),CHECK(result_structure_revision>=1)
) COMMENT='结构迁移幂等成功记录；失败事务不保留占位';
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
 VALUES(UUID_TO_BIN('019a65d0-0106-0000-8000-000000000001'),'TENANT','masterdata.product:migrate_structure','masterdata.product','migrate_structure','迁移商品结构','Migrate product structure','仅当前租户结构预览、显式迁移和幂等结果恢复；不授予编辑/启用或其他对象维护权限。');


-- 注册隔离验证页面但不创建正式菜单、不切换旧product写入口；可由已有菜单管理受控配置入口。
-- 新主档UI与结构迁移按该页面键声明，原商品档案页面保持旧契约直到数据对账完成。
