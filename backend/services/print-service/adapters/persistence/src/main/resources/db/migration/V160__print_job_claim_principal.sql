-- 领取和回执共用平台认证主体。历史领取主体未知时保留 NULL，不能从设备自报名称推断认证身份。
ALTER TABLE print_job
  ADD COLUMN claim_principal VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL
    COMMENT '领取时认证的账号 UUID 或机密客户端标识；仅来自可信打印上下文，旧领取为空'
    AFTER claimed_by;
