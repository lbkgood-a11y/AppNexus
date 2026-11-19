-- 代码登记决定入口关系；平台仅保存禁用覆盖。NULL 表示旧允许列表尚待按当前代码目录转换。
ALTER TABLE doc_page_relationships ADD COLUMN disabled_operations JSON NULL COMMENT '平台禁用的操作编码；空数组表示无额外禁用，NULL 为旧配置待迁移';

-- 管控原因单独审计，不写入普通业务数据或日志。
CREATE TABLE doc_entry_control_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审计递增标识',
 type_code VARCHAR(64) NOT NULL COMMENT '代码登记的单据类型',
 operation_key VARCHAR(128) NOT NULL COMMENT '代码登记的页面操作编码',
 enabled BOOLEAN NOT NULL COMMENT '是否允许新命令，无默认值',
 reason VARCHAR(200) NOT NULL COMMENT '管理员填写的管控原因，不得包含敏感数据',
 actor VARCHAR(64) NOT NULL COMMENT '可信操作者标识',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '管控发生时间，数据库时间'
);
