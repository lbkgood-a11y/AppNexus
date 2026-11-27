package com.appnexus.platform.persistence.capability;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.contracts.workflow.WorkflowPlatform.Option;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 已准入流程/打印公开桥接的最小目录固定SQL；后台及机器身份由原公共契约校验，不伪造租户请求。 */
@Mapper
@PlatformScoped(tables={"tenant_member","identity_account","platform_tenant"})
public interface CapabilityDirectoryMapper {
 /** @param actor 可信实际账号UUID @return 当前启用状态；不存在返回null并拒绝 */
 @Select("SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN(#{actor})")
 Boolean activeAccount(@Param("actor") String actor);
 /** @param tenant 可信租户UUID @return 启用成员最小姓名目录，不授予人员档案读取权 */
 @Select("SELECT BIN_TO_UUID(a.id) id,a.display_name name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE ORDER BY a.display_name,a.id")
 List<Option> members(@Param("tenant") String tenant);
 /** @param tenant 打印平台已验证的机器身份绑定租户UUID @return 租户显示名称，不含成员或业务数据 */
 @Select("SELECT name FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant})")
 String tenantName(@Param("tenant") String tenant);
}
