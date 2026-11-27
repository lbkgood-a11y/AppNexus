package com.appnexus.platform.persistence.identity;
import java.util.*;
import org.apache.ibatis.annotations.*;
/** 显式关联账号成员和租户状态，不能仅根据客户端 tenantId 读取租户。 */
@com.appnexus.persistence.mybatis.PlatformScoped(tables={"tenant_member","platform_tenant","identity_account"})
@Mapper
public interface TenantAccessMapper {
    /** @param tenant 平台配置用例已授权的目标租户 UUID @return 记录是否存在，不代替成员与业务授权 */
    @Select("SELECT EXISTS(SELECT 1 FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}))")
    boolean exists(String tenant);
    /** 参数为已认证账号 UUID，仅返回有效租户及成员。 */
    @Select("SELECT BIN_TO_UUID(t.id) tenant_id,BIN_TO_UUID(m.id) member_id,t.name FROM tenant_member m JOIN platform_tenant t ON t.id=m.tenant_id WHERE m.account_id=UUID_TO_BIN(#{accountId}) AND m.enabled=TRUE AND t.enabled=TRUE ORDER BY t.name,t.id")
    List<Map<String,Object>> available(String accountId);
    @Select("SELECT EXISTS(SELECT 1 FROM tenant_member m JOIN platform_tenant t ON t.id=m.tenant_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.enabled=TRUE AND t.enabled=TRUE)")
    boolean activeMember(@Param("tenant") String tenant,@Param("member") String member);
    /** 账号成员只提供授权目录摘要，无直接组织归属。 */
    @Select("SELECT BIN_TO_UUID(m.id) member_id,a.display_name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE ORDER BY a.display_name,m.id")
    java.util.List<java.util.Map<String,Object>> members(String tenant);
}
