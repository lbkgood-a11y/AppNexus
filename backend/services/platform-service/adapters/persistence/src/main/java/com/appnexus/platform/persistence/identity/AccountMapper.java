package com.appnexus.platform.persistence.identity;
import java.util.Map;
import org.apache.ibatis.annotations.*;
@com.appnexus.platform.persistence.PlatformScoped(tables={"identity_account","identity_bootstrap","oauth2_authorization"})
@Mapper
public interface AccountMapper {
    @Select("SELECT BIN_TO_UUID(id) AS id, username, display_name, password_hash, enabled, version FROM identity_account WHERE username = #{username}")
    Map<String,Object> findByUsername(String username);
    @Select("SELECT BIN_TO_UUID(id) AS id, username, display_name, password_hash, enabled, version FROM identity_account WHERE id = UUID_TO_BIN(#{id})")
    Map<String,Object> findById(String id);
    @Insert("INSERT IGNORE INTO identity_bootstrap (slot, account_id) VALUES (1, UUID_TO_BIN(#{id}))")
    int claimBootstrap(String id);
    @Insert("INSERT INTO identity_account (id, username, display_name, password_hash, enabled, version) VALUES (UUID_TO_BIN(#{id}), #{username}, #{displayName}, #{passwordHash}, TRUE, 0)")
    void insert(@Param("id") String id, @Param("username") String username,
        @Param("displayName") String displayName, @Param("passwordHash") String passwordHash);

    /** 以凭据版本进行条件更新，避免两个并发改密请求覆盖彼此。 */
    @Update("UPDATE identity_account SET password_hash=#{hash}, version=version+1 WHERE id=UUID_TO_BIN(#{id}) AND version=#{version} AND enabled=TRUE")
    int changePassword(@Param("id") String id, @Param("version") long version, @Param("hash") String hash);
    /** 密码变化后撤销该主体全部授权，阻止旧授权码或 Token 建立新会话。 */
    @Delete("DELETE FROM oauth2_authorization WHERE principal_name=#{id}")
    void revokeAuthorizations(String id);
    /** 显示名称不属于认证凭据，修改时不递增凭据版本。 */
    @Update("UPDATE identity_account SET display_name=#{name} WHERE id=UUID_TO_BIN(#{id}) AND enabled=TRUE")
    int updateDisplayName(@Param("id") String id, @Param("name") String name);
}
