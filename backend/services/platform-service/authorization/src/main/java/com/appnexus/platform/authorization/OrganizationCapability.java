package com.appnexus.platform.authorization;
/** 组织域当前受控操作；新增操作须同步权限模型、迁移、API 和授权验收。 */
public enum OrganizationCapability {
    READ, CREATE, UPDATE, DELETE, GRANT
}
