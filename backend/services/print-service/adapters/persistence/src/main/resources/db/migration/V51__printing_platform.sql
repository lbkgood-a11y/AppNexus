-- 打印模板、不可变版本、设备与任务均按租户隔离；JSON 只保存受控模板/快照，不接受脚本或外部 URL。
CREATE TABLE print_template (
  tenant_id BINARY(16) NOT NULL COMMENT '租户标识；来自可信上下文，无默认值',
  id BINARY(16) NOT NULL COMMENT '模板标识；服务端生成 UUID',
  code VARCHAR(64) NOT NULL COMMENT '租户内稳定编码，1—64 位',
  name VARCHAR(128) NOT NULL COMMENT '模板名称，1—128 位',
  category VARCHAR(32) NOT NULL COMMENT 'A4、LABEL、WAYBILL 或 RECEIPT',
  data_contract VARCHAR(64) NOT NULL COMMENT '业务服务注册的数据契约编码',
  scope_json JSON NOT NULL COMMENT '公司、组织、仓库、作业站和业务条件；缺省为空对象',
  draft_json JSON NOT NULL COMMENT '当前受控 HiPrint 草稿，不允许脚本或外部资源',
  draft_revision BIGINT NOT NULL DEFAULT 0 COMMENT '草稿乐观锁版本，从 0 递增',
  published_version INT NULL COMMENT '当前生效的不可变版本；未发布为空',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '模板是否可参与匹配；默认启用',
  created_by BINARY(16) NOT NULL COMMENT '创建账号',
  updated_by BINARY(16) NOT NULL COMMENT '最后修改账号',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (tenant_id,id), UNIQUE KEY uq_print_template_code(tenant_id,code)
) COMMENT='租户打印模板草稿';

CREATE TABLE print_template_version (
  tenant_id BINARY(16) NOT NULL, template_id BINARY(16) NOT NULL, version INT NOT NULL,
  template_json JSON NOT NULL COMMENT '发布时固定的模板 JSON',
  scope_json JSON NOT NULL COMMENT '发布时固定的匹配范围',
  data_contract VARCHAR(64) NOT NULL COMMENT '发布时固定的数据契约编码',
  published_by BINARY(16) NOT NULL, published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(tenant_id,template_id,version),
  CONSTRAINT fk_print_version_template FOREIGN KEY(tenant_id,template_id) REFERENCES print_template(tenant_id,id)
) COMMENT='不可变打印模板发布版本';

CREATE TABLE print_device (
  tenant_id BINARY(16) NOT NULL, id BINARY(16) NOT NULL,
  code VARCHAR(64) NOT NULL COMMENT '租户内设备稳定编码', name VARCHAR(128) NOT NULL,
  site_code VARCHAR(64) NOT NULL COMMENT '代理站点编码，决定任务领取范围',
  system_name VARCHAR(255) NOT NULL COMMENT '操作系统打印队列名称',
  capabilities_json JSON NOT NULL COMMENT '纸张、DPI、彩色、双面和切纸能力；缺省为空对象',
  enabled BOOLEAN NOT NULL DEFAULT TRUE, last_seen_at TIMESTAMP(6) NULL,
  created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(tenant_id,id), UNIQUE KEY uq_print_device_code(tenant_id,code)
) COMMENT='授权打印设备';

CREATE TABLE print_job (
  tenant_id BINARY(16) NOT NULL, id BINARY(16) NOT NULL,
  request_id BINARY(16) NOT NULL COMMENT '同租户同操作者写请求幂等键', actor_id BINARY(16) NOT NULL,
  source_type VARCHAR(64) NOT NULL COMMENT '业务对象类型', source_id VARCHAR(128) NOT NULL COMMENT '业务对象标识',
  template_id BINARY(16) NOT NULL, template_version INT NOT NULL,
  output_type VARCHAR(16) NOT NULL COMMENT 'PDF 或 PRINTER', device_id BINARY(16) NULL,
  copies INT NOT NULL DEFAULT 1 COMMENT '打印份数，1—99',
  data_snapshot JSON NOT NULL COMMENT '业务服务按 PRINT 用途授权后的不可变数据快照',
  state VARCHAR(32) NOT NULL COMMENT 'QUEUED、CLAIMED、SUBMITTED、SUCCEEDED、FAILED、UNKNOWN 或 CANCELLED',
  attempt INT NOT NULL DEFAULT 0, claimed_by VARCHAR(128) NULL, claim_expires_at TIMESTAMP(6) NULL,
  result_json JSON NULL, parent_job_id BINARY(16) NULL, reprint_reason VARCHAR(500) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY(tenant_id,id), UNIQUE KEY uq_print_job_request(tenant_id,actor_id,request_id),
  KEY ix_print_job_claim(tenant_id,state,created_at),
  CONSTRAINT fk_print_job_version FOREIGN KEY(tenant_id,template_id,template_version) REFERENCES print_template_version(tenant_id,template_id,version),
  CONSTRAINT fk_print_job_device FOREIGN KEY(tenant_id,device_id) REFERENCES print_device(tenant_id,id)
) COMMENT='打印及正式输出任务';

-- 模板设计、发布、设备和任务能力分别授权；普通打印权不会自动获得设计器权限。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000001'),'TENANT','print.template:read','print','template_read','查看打印模板','Read print templates','查看当前租户模板及不可变版本。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000002'),'TENANT','print.template:design','print','template_design','设计打印模板','Design print templates','新建和编辑模板草稿，不包含发布。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000003'),'TENANT','print.template:publish','print','template_publish','发布打印模板','Publish print templates','发布、停用及回滚生产模板。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000004'),'TENANT','print.device:manage','print','device_manage','管理打印设备','Manage print devices','登记、修改及停用租户设备。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000005'),'TENANT','print.job:read','print','job_read','查看打印任务','Read print jobs','查看当前租户打印记录。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000006'),'TENANT','print.job:submit','print','job_submit','提交打印任务','Submit print jobs','使用发布模板提交预览、输出和打印。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000007'),'TENANT','print.job:reprint','print','job_reprint','人工补打','Reprint jobs','填写原因后创建关联补打任务。'),
(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000008'),'TENANT','print.agent:operate','print','agent_operate','运行打印代理','Operate print agents','领取设备任务并报告受控回执。');

INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
 SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
 FROM access_role r JOIN access_permission p ON p.resource='print' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id)
 SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id
 JOIN access_permission p ON p.resource='print' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO platform_menu(id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type,enabled)
VALUES(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000010'),'MENU','builtin.navigation.print','打印管理','Print management','/print/management','print/index','ri/printer-line','print.template:read',22,'TENANT',TRUE);
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0051-7000-8000-000000000010'),'print');
UPDATE access_role SET version=version+1 WHERE system_key LIKE 'TENANT_ADMIN:%';
