-- 商品审核申请作为独立业务记录接现有工作流，不将节点通过冒充整个流程批准。
CREATE TABLE md_product_approval (
 id BINARY(16) NOT NULL COMMENT '服务端生成审核申请UUID，工作流objectId引用此记录',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 product_id BINARY(16) NOT NULL COMMENT '被审核商品UUID',
 request_key BINARY(16) NOT NULL COMMENT '客户端稳定幂等UUID，可信账号作用域内不重复提交',
 product_version BIGINT NOT NULL COMMENT '提交后冻结商品版本，流程依据只读',
 applicant_id BINARY(16) NOT NULL COMMENT '可信申请账号UUID',
 organization_id BINARY(16) NOT NULL COMMENT '申请人有效任职组织UUID，公开平台契约验证',
 state VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/APPROVED/REJECTED/WITHDRAWN',
 instance_id VARCHAR(64) NULL COMMENT '现有工作流公开实例ID，非凭据',
 resolution_id VARCHAR(64) NULL COMMENT '现有业务策略解析ID，固定发布版本',
 basis_snapshot JSON NOT NULL COMMENT '提交时完整商品快照，禁止业务原文进入流程变量',
 pending_product BINARY(16) GENERATED ALWAYS AS (CASE WHEN state='SUBMITTED' THEN product_id ELSE NULL END) STORED COMMENT '同一商品最多一条处理中申请',
 delivery_id VARCHAR(64) NULL COMMENT '终态可信调用幂等ID，不接受浏览器成功声明',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，微秒，与正文同事务',
 PRIMARY KEY(id),UNIQUE KEY uk_pa_request(tenant_id,applicant_id,request_key),UNIQUE KEY uk_pa_pending(tenant_id,pending_product),KEY ix_pa_product(tenant_id,product_id,created_at),
 CONSTRAINT fk_pa_product FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id),
 CONSTRAINT ck_pa_state CHECK(state IN ('SUBMITTED','APPROVED','REJECTED','WITHDRAWN')),
 CONSTRAINT ck_pa_version CHECK(product_version>=1)
) COMMENT='商品完整快照审核轮次；审核结果不自动替代启用操作';
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0117-0000-8000-000000000001'),'TENANT','masterdata.product:submit_approval','masterdata.product','submit_approval','提交商品审核','Submit product approval','提交完整商品快照到现有审批，不授予审批办理或启用权限');
