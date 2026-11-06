-- 平台菜单资源目录是配置元数据；不把前端可见性当作接口授权依据。
CREATE TABLE platform_menu (
 id BINARY(16) NOT NULL PRIMARY KEY,
 parent_id BINARY(16) NULL,
 type VARCHAR(16) NOT NULL,
 code VARCHAR(100) NOT NULL,
 title_zh VARCHAR(100) NOT NULL,
 title_en VARCHAR(100) NOT NULL,
 route_path VARCHAR(200) NULL,
 component_key VARCHAR(200) NULL,
 icon VARCHAR(100) NULL,
 permission_code VARCHAR(100) NULL,
 sort_order INT NOT NULL DEFAULT 0,
 visible BOOLEAN NOT NULL DEFAULT TRUE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT uk_platform_menu_code UNIQUE(code),
 CONSTRAINT fk_platform_menu_parent FOREIGN KEY(parent_id) REFERENCES platform_menu(id),
 CONSTRAINT ck_platform_menu_type CHECK(type IN ('DIRECTORY','MENU','BUTTON')),
 CONSTRAINT ck_platform_menu_sort CHECK(sort_order BETWEEN 0 AND 9999)
);

-- 菜单写操作审计只保存节点标识与版本，不保存完整配置正文。
CREATE TABLE platform_menu_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_account_id BINARY(16) NOT NULL,
 action VARCHAR(32) NOT NULL,
 menu_id BINARY(16) NOT NULL,
 menu_version BIGINT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
