package com.appnexus.platform.identity;

/** 邮箱或手机号已被其他全局账号占用；不暴露冲突账号身份。 */
public final class AccountContactConflictException extends RuntimeException {
    public AccountContactConflictException() {
        super("Account contact already in use");
    }
}
