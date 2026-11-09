-- 强制字段展示限制独立于普通功能授权；多角色命中时取最少披露结果，不能被其他角色绕过。
CREATE TABLE access_role_field_security (
 role_id BINARY(16) NOT NULL COMMENT '限制所属角色 UUID，必填；停用角色不再提供该限制',
 resource_type VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已登记且接通执行的资源标识，必填',
 field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源登记字段，必填；不得填写 SQL、属性路径或任意列名',
 display_mode VARCHAR(16) NOT NULL COMMENT 'HIDDEN 隐藏、MASKED 部分遮盖；不配置表示不额外限制，不自动授予原文权',
 PRIMARY KEY(role_id,resource_type,field_code),
 CONSTRAINT fk_role_field_security_role FOREIGN KEY(role_id) REFERENCES access_role(id),
 CONSTRAINT ck_role_field_security_mode CHECK(display_mode IN('HIDDEN','MASKED'))
);
