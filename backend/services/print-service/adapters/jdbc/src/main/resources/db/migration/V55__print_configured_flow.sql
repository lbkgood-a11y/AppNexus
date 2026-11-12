-- 场景将已发布模板、字段契约和缺省份数关联；同租户显式选择设备，不提供跨租户路径。
CREATE TABLE print_data_contract (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填',
 code VARCHAR(64) NOT NULL COMMENT '带版本的契约编码，1—64 字符，创建后不可修改',
 definition_json JSON NOT NULL COMMENT '中文、英文名称与字段列表，最多 50 字段；仅受控文本和明细',
 created_by BINARY(16) NOT NULL COMMENT '契约设计者账号 UUID',
 PRIMARY KEY(tenant_id,code)
) COMMENT='租户不可变打印数据契约；调整字段须创建新版本';

CREATE TABLE print_scene (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填，无默认值',
 id BINARY(16) NOT NULL COMMENT '场景 UUID，服务端生成，无默认值',
 code VARCHAR(64) NOT NULL COMMENT '租户内场景编码，1—64 字符，无默认值',
 name VARCHAR(128) NOT NULL COMMENT '场景名称，1—128 字符，无默认值',
 data_contract VARCHAR(64) NOT NULL COMMENT '已注册字段契约编码，无默认值',
 template_id BINARY(16) NOT NULL COMMENT '绑定已发布模板 UUID，无默认值',
 copies INT NOT NULL DEFAULT 1 COMMENT '缺省打印份数，1—99，用户提交时可调整',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '场景是否允许提交，默认启用',
 revision BIGINT NOT NULL DEFAULT 0 COMMENT '配置乐观锁版本，默认 0，每次保存递增',
 updated_by BINARY(16) NOT NULL COMMENT '配置操作者 UUID，无默认值',
 PRIMARY KEY(tenant_id,id), UNIQUE KEY uq_print_scene_code(tenant_id,code),
 FOREIGN KEY(tenant_id,template_id) REFERENCES print_template(tenant_id,id)
) COMMENT='租户配置式打印场景';

-- 手工录入单据是真实用户数据，不是预览样本；保留版本，提交只读取服务端快照。
CREATE TABLE print_document (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填',
 id BINARY(16) NOT NULL COMMENT '录入单据 UUID，服务端生成',
 scene_id BINARY(16) NOT NULL COMMENT '所属场景 UUID，必填',
 number VARCHAR(128) NOT NULL COMMENT '用户填写单号，1—128 字符，场景内唯一',
 data_json JSON NOT NULL COMMENT '按契约白名单校验的用户录入数据，最大 5 MB',
 revision BIGINT NOT NULL DEFAULT 0 COMMENT '单据版本，默认 0，修改时递增',
 created_by BINARY(16) NOT NULL COMMENT '录入账号 UUID，打印和读取仅限本人',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，数据库会话 UTC',
 PRIMARY KEY(tenant_id,id), UNIQUE KEY uq_print_document_number(tenant_id,scene_id,number),
 FOREIGN KEY(tenant_id,scene_id) REFERENCES print_scene(tenant_id,id)
) COMMENT='配置式打印手工单据';

-- 队列仅由专用代理认证上报，人员选择后登记设备；上报不自动授予设备管理权限。
CREATE TABLE print_agent_queue (
 tenant_id BINARY(16) NOT NULL COMMENT '代理凭据绑定的租户 UUID',
 site_code VARCHAR(64) NOT NULL COMMENT '代理站点编码，1—64 字符',
 agent_id VARCHAR(128) NOT NULL COMMENT '代理实例标识，1—128 字符',
 queue_name VARCHAR(255) NOT NULL COMMENT '客户端发现的系统队列名，1—255 字符',
 seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最后上报时间，超过 120 秒视为离线',
 PRIMARY KEY(tenant_id,site_code,agent_id,queue_name)
) COMMENT='认证代理上报的客户端打印队列';
