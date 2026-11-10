package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 密码恢复平台仓储；所有操作由恢复用例在账号锁事务内完成。 */
@Mapper
@PlatformScoped(tables={"identity_account", "identity_password_recovery"})
public interface PasswordRecoveryMapper {
    /**
     * @param username 登录名，使用参数绑定
     * @return 已锁定的有效账号与版本，目标不存在时为 null
     */
    @Select("SELECT BIN_TO_UUID(id) id,version FROM identity_account WHERE username=#{username} AND enabled=TRUE FOR UPDATE")
    Map<String,Object> lock(String username);
    /**
     * @param account 全局账号 UUID
     * @return 已锁定账号凭据版本，不存在或停用时为 null
     */
    @Select("SELECT version FROM identity_account WHERE id=UUID_TO_BIN(#{account}) AND enabled=TRUE FOR UPDATE")
    Long version(String account);
    /**
     * 新签发覆盖同账号旧凭证；版本和到期时间均来自服务端。
     * @param account 已锁定的有效账号 UUID
     * @param hash 32 字节凭证摘要，不得保存原文
     * @param version 签发时凭据版本，版本变化后该凭证失效
     * @param expires 服务端 UTC 到期时间，签发后 600 秒
     */
    @Insert("INSERT INTO identity_password_recovery(account_id,token_hash,credential_version,expires_at) VALUES(UUID_TO_BIN(#{account}),#{hash},#{version},#{expires}) ON DUPLICATE KEY UPDATE token_hash=#{hash},credential_version=#{version},expires_at=#{expires}")
    void issue(@Param("account") String account,@Param("hash") byte[] hash,@Param("version") long version,@Param("expires") Instant expires);
    /**
     * 只允许删除仍匹配当前凭据版本且未到期的凭证，删除条数为 1 才获得改密资格。
     * @param account 已锁定的有效账号 UUID
     * @param hash 当前请求凭证的 SHA-256 摘要
     * @param version 当前权威凭据版本，不能来自客户端
     * @return 成功消费为 1；不存在、过期、重放或版本变化为 0
     */
    @Delete("DELETE FROM identity_password_recovery WHERE account_id=UUID_TO_BIN(#{account}) AND token_hash=#{hash} AND credential_version=#{version} AND expires_at>CURRENT_TIMESTAMP(6)")
    int consume(@Param("account") String account,@Param("hash") byte[] hash,@Param("version") long version);
}
