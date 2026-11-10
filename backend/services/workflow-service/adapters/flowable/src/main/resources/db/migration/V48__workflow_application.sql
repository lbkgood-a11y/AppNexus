-- 工作流自有持久化；所有主键和查询均带 tenant_id，不建立对平台或业务表的跨域外键。
-- JSON 仅保存已注册能力声明的非敏感流转字段、流程模型与受限任务输入；原始业务实体保留在业务服务。
CREATE TABLE wf_definition (
 tenant_id VARCHAR(64) NOT NULL COMMENT '租户标识，必填，无默认值',
 id VARCHAR(64) NOT NULL COMMENT '流程定义标识，必填',
 revision BIGINT NOT NULL DEFAULT 0 COMMENT '草稿乐观锁版本，从0开始',
 model LONGTEXT NOT NULL COMMENT '受控工作流JSON模型，不接受脚本',
 enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许新发起，默认否，首次发布启用',
 published INT NOT NULL DEFAULT 0 COMMENT '最新发布版本，0表示未发布',
 PRIMARY KEY(tenant_id,id)
);
-- 发布版本不可变；停用只作用于后续匹配，不删除历史部署。
CREATE TABLE wf_version (
 tenant_id VARCHAR(64) NOT NULL, definition_id VARCHAR(64) NOT NULL, version INT NOT NULL,
 model LONGTEXT NOT NULL, bpmn LONGTEXT NOT NULL, engine_definition VARCHAR(128) NOT NULL,
 published_by VARCHAR(64) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,definition_id,version)
);
-- active_key 只在运行中非空；唯一键在并发提交时阻止同单据同用途重复发起，历史行允许NULL。
CREATE TABLE wf_instance (
 tenant_id VARCHAR(64) NOT NULL, id VARCHAR(64) NOT NULL, definition_id VARCHAR(64) NOT NULL, definition_version INT NOT NULL,
 object_type VARCHAR(64) NOT NULL, object_id VARCHAR(128) NOT NULL, object_version BIGINT NOT NULL,
 purpose VARCHAR(64) NOT NULL, organization_id VARCHAR(64) NOT NULL, starter VARCHAR(64) NOT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'RUNNING', result VARCHAR(32), active_key CHAR(64),
 engine_id VARCHAR(128), snapshot LONGTEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ended_at TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id), UNIQUE KEY uk_wf_active(tenant_id,active_key)
);
-- 一行对应一次人员任务，保留原选人与办理历史；业务内容不存任务表，暂存由业务能力处理。
CREATE TABLE wf_task (
 tenant_id VARCHAR(64) NOT NULL, id VARCHAR(128) NOT NULL, instance_id VARCHAR(64) NOT NULL, node_id VARCHAR(64) NOT NULL,
 node_name VARCHAR(100) NOT NULL COMMENT '发布时节点名称，用于工作台主要展示',
 actor VARCHAR(64), state VARCHAR(32) NOT NULL DEFAULT 'OPEN', decision VARCHAR(32), comment VARCHAR(2000) COMMENT '本次办理意见，最多2000字符，业务内容另由业务服务存储', deadline TIMESTAMP(6) NOT NULL,
 checked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近成员资格复核时间，默认创建时间；按最早时间分批扫描避免饥饿',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), completed_at TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id), INDEX ix_wf_inbox(tenant_id,actor,state)
);
-- ACTION/WAIT/TERMINAL 共用可靠交付记录；幂等ID在重试中保持不变，租约过期可重新领取。
CREATE TABLE wf_job (
 tenant_id VARCHAR(64) NOT NULL, id VARCHAR(64) NOT NULL, instance_id VARCHAR(64) NOT NULL, node_id VARCHAR(64) NOT NULL,
 execution_id VARCHAR(128), capability VARCHAR(64) NOT NULL, kind VARCHAR(16) NOT NULL,
 payload LONGTEXT NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'READY', attempts INT NOT NULL DEFAULT 0,
 max_attempts INT NOT NULL, due_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), deadline TIMESTAMP(6) NOT NULL,
 lease_token VARCHAR(64), lease_until TIMESTAMP(6), result LONGTEXT, error_code VARCHAR(64),
 PRIMARY KEY(tenant_id,id), INDEX ix_wf_job_due(state,due_at)
);
-- 每个写请求必须携带请求UUID；相同键不同内容拒绝，响应与业务写入同事务保存。
CREATE TABLE wf_request (
 tenant_id VARCHAR(64) NOT NULL, actor VARCHAR(64) NOT NULL, request_id VARCHAR(64) NOT NULL,
 fingerprint CHAR(64) NOT NULL, response LONGTEXT,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(tenant_id,actor,request_id)
);
-- 审计追加写，避免修改业务数据或暴露业务敏感字段；history仅允许实例参与者或运行管理员读取。
CREATE TABLE wf_audit (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id VARCHAR(64) NOT NULL, instance_id VARCHAR(64),
 actor VARCHAR(64) NOT NULL, action VARCHAR(64) NOT NULL, target VARCHAR(128) NOT NULL,
 from_actor VARCHAR(64) COMMENT '分配变更前账号，首次分配为空', to_actor VARCHAR(64) COMMENT '分配变更后账号，待重新分配为空',
 detail VARCHAR(500) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id), INDEX ix_wf_history(tenant_id,instance_id,id)
);

-- 抄送只建立受限事实可见性，不授予任务处理权或业务原文查看权。
CREATE TABLE wf_copy (tenant_id VARCHAR(64) NOT NULL,instance_id VARCHAR(64) NOT NULL,actor VARCHAR(64) NOT NULL,PRIMARY KEY(tenant_id,instance_id,actor));
