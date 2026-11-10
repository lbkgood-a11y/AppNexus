package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 全局账号 MFA 仓储；写入前必须按账号行、MFA 行的固定顺序加锁。 */
@Mapper
@PlatformScoped(tables={"identity_account", "identity_account_mfa", "identity_mfa_recovery", "oauth2_authorization"})
public interface MfaMapper {
    /**
     * @param account 不可变账号 UUID
     * @return 已启用账号标识；无有效账号时返回 null
     */
    @Select("SELECT BIN_TO_UUID(id) FROM identity_account WHERE id=UUID_TO_BIN(#{account}) AND enabled=TRUE FOR UPDATE")
    String lockAccount(String account);
    /**
     * @param account 不可变账号 UUID
     * @return 因子状态，不返回原始密钥
     */
    @Select("SELECT enabled FROM identity_account_mfa WHERE account_id=UUID_TO_BIN(#{account})")
    Boolean enabled(String account);
    /**
     * @param account 已锁定的有效账号 UUID
     * @return 加密密钥与计数器，仅供认证适配器内部使用
     */
    @Select("SELECT encrypted_secret,enabled,pending_until,last_counter FROM identity_account_mfa WHERE account_id=UUID_TO_BIN(#{account}) FOR UPDATE")
    Map<String,Object> lock(String account);
    /**
     * 保存待确认密钥，调用方必须先拒绝覆盖已启用因子。
     * @param account 已锁定的有效全局账号 UUID
     * @param secret 带随机 nonce 和认证标签的 AES-GCM 密文，不能传入原始密钥
     * @param until 服务端 UTC 到期时间，固定为生成后 600 秒
     */
    @Insert("INSERT INTO identity_account_mfa(account_id,encrypted_secret,pending_until) VALUES(UUID_TO_BIN(#{account}),#{secret},#{until}) ON DUPLICATE KEY UPDATE encrypted_secret=#{secret},pending_until=#{until},last_counter=-1")
    void pending(@Param("account") String account,@Param("secret") byte[] secret,@Param("until") Instant until);
    /**
     * 在持有行锁时推进计数器；启用状态只能由成功验证码确认。
     * @param account 已锁定的有效账号 UUID
     * @param counter 已成功验证的 30 秒计数器，必须大于历史值
     */
    @Update("UPDATE identity_account_mfa SET enabled=TRUE,last_counter=#{counter} WHERE account_id=UUID_TO_BIN(#{account})")
    void advance(@Param("account") String account,@Param("counter") long counter);
    /**
     * 恢复码摘要必须来自 128 位密码学随机数，不接受用户自行设置恢复码。
     * @param account 已锁定账号 UUID
     * @param hash 32 字节 SHA-256 摘要，不能保存恢复码原文
     */
    @Insert("INSERT INTO identity_mfa_recovery(account_id,code_hash) VALUES(UUID_TO_BIN(#{account}),#{hash})")
    void recovery(@Param("account") String account,@Param("hash") byte[] hash);
    /**
     * 单次消费恢复码；删除条数为 1 才能认证成功。
     * @param account 已锁定账号 UUID
     * @param hash 提交恢复码的 32 字节 SHA-256 摘要
     * @return 删除条数，0 表示无效或已消费
     */
    @Delete("DELETE FROM identity_mfa_recovery WHERE account_id=UUID_TO_BIN(#{account}) AND code_hash=#{hash}")
    int consume(@Param("account") String account,@Param("hash") byte[] hash);
    /** 删除因子时通过外键级联清理剩余恢复码。 */
    @Delete("DELETE FROM identity_account_mfa WHERE account_id=UUID_TO_BIN(#{account})")
    void remove(String account);
    /** 因子变更使已有会话失效；不能让原密码会话继承新认证强度。 */
    @Update("UPDATE identity_account SET version=version+1 WHERE id=UUID_TO_BIN(#{account})")
    void invalidateSessions(String account);
    /** 因子变更撤销全部旧 OAuth 授权，与会话版本推进处于同一事务。 */
    @Delete("DELETE FROM oauth2_authorization WHERE principal_name=#{account}")
    void invalidateTokens(String account);
}
