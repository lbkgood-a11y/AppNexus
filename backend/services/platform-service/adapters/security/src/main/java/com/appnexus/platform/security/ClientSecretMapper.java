package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 客户端旧密钥窗口固定查询；仅验证当前已注册哈希匹配的同一客户端，不记录密钥或返回外部。 */
@Mapper
@PlatformScoped(tables={"integration_client","oauth2_registered_client","platform_tenant"})
public interface ClientSecretMapper {
 /** @param encoded 框架已加载的当前注册Argon2id哈希，禁止日志记录 @return 同客户端600秒窗口内的旧哈希；不匹配或停用时空集合 */
 @Select("SELECT c.previous_secret_hash FROM integration_client c JOIN oauth2_registered_client r ON r.id=c.id JOIN platform_tenant t ON t.id=c.tenant_id WHERE r.client_secret=#{encoded} AND c.enabled=TRUE AND t.enabled=TRUE AND c.previous_secret_until>CURRENT_TIMESTAMP(6) AND c.previous_secret_hash IS NOT NULL")
 List<String> previous(@Param("encoded") String encoded);
}
