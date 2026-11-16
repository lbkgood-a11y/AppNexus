-- 全局记录审计：新增列不回填历史操作者或历史时间，避免把迁移执行时刻伪装成业务事实。
-- 新写入由共享 JDBC 审计层在原业务事务内补齐；UTC 微秒时间来自数据库，账号只取可信执行主体。

ALTER TABLE `leave_poc_approval_cycle`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `leave_poc_delivery`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `leave_poc_request`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `leave_poc_submission`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';
