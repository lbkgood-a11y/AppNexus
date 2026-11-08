package com.appnexus.platform.authorization;

/** 角色管理稳定业务错误；适配器只向客户端暴露 code，不返回 SQL 或数据库约束名。 */
public final class RoleManagementException extends RuntimeException {
    private final String code;
    /** @param code 稳定错误码 @param message 不含其他租户或内部持久化细节的说明 */
    public RoleManagementException(String code, String message) { super(message);this.code=code; }
    public String code() { return code; }
}
