package com.appnexus.platform.identity;

import java.util.UUID;

/** 密码找回端口；重置凭证短时有效、单次消费，不能用于绕过已有第二因子。 */
public interface PasswordRecovery {
    /**
     * 消费本人 MFA 恢复码，签发一次性重置凭证。
     * @param username 登录名，最长 100 字符，错误统一返回认证失败
     * @param recoveryCode 32 位十六进制恢复码；不接受短 TOTP 作为密码恢复依据
     * @return 64 位十六进制随机凭证，有效 10 分钟，仅返回一次
     */
    String recover(String username,String recoveryCode);
    /**
     * 受权管理员为普通有效账号签发重置凭证；不能重置本人或平台管理员。
     * @param actor 已认证的平台管理员，事务内再次锁定复核
     * @param account 目标全局账号 UUID；不因此修改第二因子或业务授权
     * @return 单次重置凭证，必须通过受控渠道交付本人，禁止写入日志
     */
    String issue(UUID actor,UUID account);
    /**
     * 原子消费凭证并改密、撤销全部旧会话和 OAuth 授权。
     * @param username 不可变登录名，需与凭证目标一致
     * @param token 64 位十六进制单次凭证，过期、重放及目标不匹配统一拒绝
     * @param passwordHash 已通过密码策略校验的 Argon2id 哈希，禁止明文
     * @return 是否成功；不返回账号是否存在或凭据版本
     */
    boolean complete(String username,String token,String passwordHash);
}
