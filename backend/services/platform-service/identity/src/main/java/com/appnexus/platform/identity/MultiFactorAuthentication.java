package com.appnexus.platform.identity;

import java.util.List;
import java.util.UUID;

/** 本地第二因子端口；账号来自服务端认证，所有验证码和恢复码只能单次使用。 */
public interface MultiFactorAuthentication {
    /** @return 当前部署是否已配置用于保护 TOTP 密钥的独立加密密钥 */
    boolean available();
    /**
     * @param account 不可变全局账号 UUID
     * @return 是否已完成第二因子绑定
     */
    boolean enabled(UUID account);
    /**
     * 创建 10 分钟有效的待确认密钥；已启用账号不能覆盖现有因子。
     * @param account 已通过当前密码复核的账号
     * @return Base32 密钥，仅在此次响应返回，不可写日志或浏览器持久存储
     */
    String begin(UUID account);
    /**
     * 确认 TOTP 绑定，原子生成恢复码并撤销旧认证。
     * @param account 已通过当前密码复核的账号
     * @param code 六位 TOTP，30 秒步长，允许前后各一个时间窗口
     * @return 10 个恢复码，仅返回一次；数据库只保存摘要
     */
    List<String> activate(UUID account, String code);
    /**
     * 原子消费一个验证码或恢复码；数据库行锁拒绝并发重放。
     * @param account 不可变账号标识
     * @param code 六位 TOTP 或 32 位十六进制恢复码
     * @return 因子有效且成功消费时为 true；失败不泄露具体原因
     */
    boolean verify(UUID account, String code);
    /**
     * 关闭第二因子并撤销旧认证；调用方必须复核当前密码和第二因子。
     * @param account 本人账号标识
     * @param code 当前有效 TOTP 或恢复码
     * @return 是否验证成功并完成关闭
     */
    boolean disable(UUID account, String code);
}
