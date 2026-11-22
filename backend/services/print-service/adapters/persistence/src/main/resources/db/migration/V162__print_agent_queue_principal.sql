-- 代理可显示相同名称，隔离以上报时可信认证主体为准。
-- 旧数据保留空主体并按现有 120 秒窗口自然下线；空主体不能被请求冒认。
ALTER TABLE print_agent_queue
  ADD COLUMN agent_principal VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL DEFAULT ''
    COMMENT '上报时可信认证账号或机密客户端标识；历史记录为空，不推断身份'
    AFTER agent_id,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY(tenant_id,site_code,agent_id,agent_principal,queue_name);
