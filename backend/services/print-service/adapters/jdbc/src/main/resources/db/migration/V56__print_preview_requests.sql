-- 预览请求固定输出快照，确认打印不重新读取可能变化的模板或业务数据。
CREATE TABLE print_output_request (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，无默认值',
 id BINARY(16) NOT NULL COMMENT '输出请求 UUID，无默认值',
 owner VARCHAR(128) NOT NULL COMMENT '认证人员或外部客户端标识，不接受请求正文指定',
 request_id BINARY(16) NOT NULL COMMENT '调用方幂等 UUID，同主体内唯一',
 payload_hash CHAR(64) NOT NULL COMMENT '请求规范 JSON 的 SHA-256，拒绝同键异参',
 mode VARCHAR(16) NOT NULL COMMENT 'SILENT 直接入队，PREVIEW 生成 PDF 后等待确认',
 job_id BINARY(16) NOT NULL COMMENT '初始打印或 PDF 任务 UUID',
 device_id BINARY(16) NULL COMMENT '目标打印机，预览阶段允许缺省，确认时必填',
 copies INT NOT NULL COMMENT '实际打印份数，1—99',
 confirmed_job_id BINARY(16) NULL COMMENT '确认后唯一打印任务，禁止重复确认出纸',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '请求时间，UTC',
 PRIMARY KEY(tenant_id,id),UNIQUE KEY uq_print_output_request(tenant_id,owner,request_id),
 FOREIGN KEY(tenant_id,job_id) REFERENCES print_job(tenant_id,id)
) COMMENT='统一静默与预览后打印请求';
