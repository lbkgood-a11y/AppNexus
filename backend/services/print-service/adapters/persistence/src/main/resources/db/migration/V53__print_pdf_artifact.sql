-- PDF 产物归属当前租户任务；最多 10 MiB 由应用写入校验，数据库不保存客户端文件路径。
CREATE TABLE print_job_artifact (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，必填，无默认值',
  job_id BINARY(16) NOT NULL COMMENT '所属 PDF 任务 UUID，必填，租户内唯一',
  pdf_bytes MEDIUMBLOB NOT NULL COMMENT '代理生成的 PDF 原文，最大 10485760 字节，无默认值',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '产物接收时间，数据库会话使用 UTC',
  PRIMARY KEY(tenant_id,job_id),
  CONSTRAINT fk_print_artifact_job FOREIGN KEY(tenant_id,job_id) REFERENCES print_job(tenant_id,id)
) COMMENT='租户 PDF 输出产物，下载必须复核打印记录权限';
