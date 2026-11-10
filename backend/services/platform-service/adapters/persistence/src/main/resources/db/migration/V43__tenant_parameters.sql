-- 租户业务参数仅保存明确登记的非敏感配置，不作为系统密钥存储或任意脚本入口。
CREATE TABLE tenant_parameter (
    -- 可信租户 UUID，所有读写必须与有效成员上下文一致。
    tenant_id BINARY(16) NOT NULL,
    -- 受控参数编码，不能创建任意数据库连接、安全密钥或执行参数。
    parameter_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- 非敏感参数值，最长 500 字符，参数定义决定校验及环境边界。
    parameter_value VARCHAR(500) NOT NULL,
    -- 乐观锁版本，首次零，更新必须匹配，防止并发覆盖。
    version INT NOT NULL DEFAULT 0,
    -- 最后修改账号，服务端认证取得，不接受请求体指定。
    updated_by BINARY(16) NOT NULL,
    -- 最后修改时间，UTC，仅作为审计依据。
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id,parameter_key)
);
CREATE TABLE tenant_parameter_audit (
    -- 追加审计标识，不暴露业务原值。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 固定租户边界，不允许跨租户查询。
    tenant_id BINARY(16) NOT NULL,
    -- 操作者不可变账号 UUID，不保存姓名或凭据。
    actor_id BINARY(16) NOT NULL,
    -- 受控参数编码，不保存参数原值以免未来新增配置泄露。
    parameter_key VARCHAR(100) NOT NULL,
    -- 修改后版本，关联并发操作依据。
    version INT NOT NULL,
    -- 操作时间，UTC，数据库自动生成。
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
