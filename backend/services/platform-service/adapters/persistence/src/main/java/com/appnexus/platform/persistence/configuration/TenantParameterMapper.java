package com.appnexus.platform.persistence.configuration;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
/** 租户展示参数固定持久化；不提供系统安全配置或任意SQL入口。 */
@Mapper
@PlatformScoped(tables={"tenant_parameter","tenant_parameter_audit","platform_tenant"})
public interface TenantParameterMapper extends com.appnexus.platform.configuration.port.TenantParameterRepository {
 /** list 固定SQL；可信租户及实际操作者由用例验证，写入及审计保持同事务。 */
 @Select("SELECT parameter_key,parameter_value,version FROM tenant_parameter WHERE tenant_id=UUID_TO_BIN(#{tenant})")
 List<Map<String,Object>> list(@Param("tenant") String tenant);

 /** lockTenant 固定SQL；可信租户及实际操作者由用例验证，写入及审计保持同事务。 */
 @Select("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> lockTenant(@Param("tenant") String tenant);

 /** create 固定SQL；可信租户及实际操作者由用例验证，写入及审计保持同事务。 */
 @Insert("INSERT INTO tenant_parameter(tenant_id,parameter_key,parameter_value,updated_by,created_by,created_at) VALUES(UUID_TO_BIN(#{tenant}),#{key},#{value},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6))")
 int create(@Param("tenant") String tenant,@Param("key") String key,@Param("value") String value,@Param("actor") String actor);

 /** update 固定SQL；可信租户及实际操作者由用例验证，写入及审计保持同事务。 */
 @Update("UPDATE tenant_parameter SET parameter_value=#{value},version=version+1,updated_by=UUID_TO_BIN(#{actor}) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND parameter_key=#{key} AND version=#{version}")
 int update(@Param("value") String value,@Param("actor") String actor,@Param("tenant") String tenant,@Param("key") String key,@Param("version") int version);

 /** audit 固定SQL；可信租户及实际操作者由用例验证，写入及审计保持同事务。 */
 @Insert("INSERT INTO tenant_parameter_audit(tenant_id,actor_id,parameter_key,version,created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),#{key},#{next},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6))")
 int audit(@Param("tenant") String tenant,@Param("actor") String actor,@Param("key") String key,@Param("next") int next);
}
