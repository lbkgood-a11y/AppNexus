package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.api.masterdata.MasterData.*;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** 主数据固定枚举展开的Mapper；XML无自由表列替换，原用例负责行/字段准入和完整事务。 */
@Mapper
@PlatformScoped(tables={"md_partner","md_warehouse","md_storage_area","md_storage_location","md_country_region","md_subdivision"})
public interface MasterRecordMapper extends DataAccessDeclaration.Executor {
 /** @return 固定租户主数据及由统一范围和字段限制保护的仓储资源 */
 @Override default Set<String> enforcedResources(){return Set.of(Kind.COUNTRY_REGION.resource(),Kind.SUBDIVISION.resource(),Kind.STORAGE_AREA.resource(),Kind.STORAGE_LOCATION.resource(), Kind.WAREHOUSE.resource());}
 /** 固定对象新增，创建与修改审计来自实际主体，SQL时间为UTC。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.partner",tenant="tenant",recordId="id",actions="create",when="kind",whenValue="PARTNER")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.warehouse",tenant="tenant",recordId="id",actions="create",when="kind",target="input.organizationId",whenValue="WAREHOUSE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagelocation",tenant="tenant",recordId="id",actions="create",when="kind",parentKey="values.parentCode",whenValue="STORAGE_LOCATION")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagearea",tenant="tenant",recordId="id",actions="create",when="kind",parentKey="values.parentCode",whenValue="STORAGE_AREA")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.countryregion:create",serviceKeys={"id"},when="kind",whenValue="COUNTRY_REGION")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.subdivision:create",serviceKeys={"id"},when="kind",whenValue="SUBDIVISION")
 int create(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 固定对象CAS修改；编码不可改变，失败返回冲突。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.partner",tenant="tenant",recordId="id",actions="update",when="kind",whenValue="PARTNER")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.warehouse",tenant="tenant",recordId="id",actions="update",when="kind",target="input.organizationId",whenValue="WAREHOUSE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagelocation",tenant="tenant",recordId="id",actions="update",when="kind",whenValue="STORAGE_LOCATION")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagearea",tenant="tenant",recordId="id",actions="update",when="kind",whenValue="STORAGE_AREA")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.countryregion:update",serviceKeys={"id"},when="kind",whenValue="COUNTRY_REGION")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.subdivision:update",serviceKeys={"id"},when="kind",whenValue="SUBDIVISION")
 int update(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values);
 /** 固定对象独立启停，不能通过普通编辑隐式获得。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.partner",tenant="tenant",recordId="id",actions="set_status",when="kind",whenValue="PARTNER")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.warehouse",tenant="tenant",recordId="id",actions="set_status",when="kind",whenValue="WAREHOUSE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagelocation",tenant="tenant",recordId="id",actions="set_status",when="kind",whenValue="STORAGE_LOCATION")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagearea",tenant="tenant",recordId="id",actions="set_status",when="kind",whenValue="STORAGE_AREA")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.countryregion:set_status",serviceKeys={"id"},when="kind",whenValue="COUNTRY_REGION")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.subdivision:set_status",serviceKeys={"id"},when="kind",whenValue="SUBDIVISION")
 int status(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("enabled") boolean enabled,@Param("version") long version,@Param("actor") String actor);
 /** 授权父记录中的原始字段用于写保护合并，不返回未授权原文。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.partner",tenant="tenant",recordId="id",actions="update",when="kind",whenValue="PARTNER")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.warehouse",tenant="tenant",recordId="id",actions="update",when="kind",whenValue="WAREHOUSE")
 List<Map<String,String>> previous(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id);
 /** 当前业务修改同事务推进修改审计，不覆盖创建信息。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.partner",tenant="tenant",recordId="id",actions={"update","set_status"},when="kind",whenValue="PARTNER")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.warehouse",tenant="tenant",recordId="id",actions={"update","set_status"},when="kind",whenValue="WAREHOUSE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagelocation",tenant="tenant",recordId="id",actions={"update","set_status"},when="kind",whenValue="STORAGE_LOCATION")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.storagearea",tenant="tenant",recordId="id",actions={"update","set_status"},when="kind",whenValue="STORAGE_AREA")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.countryregion:audit",serviceKeys={"id"},when="kind",whenValue="COUNTRY_REGION")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.subdivision:audit",serviceKeys={"id"},when="kind",whenValue="SUBDIVISION")
 int audit(@Param("tenant") String tenant,@Param("kind") com.appnexus.masterdata.api.masterdata.MasterData.Kind kind,@Param("id") String id,@Param("actor") String actor);
 /** 客商归属已分别验证原记录及目标范围。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.partner",tenant="tenant",recordId="id",actions={"create","update"},target="organization")
 int organization(@Param("tenant") String tenant,@Param("id") String id,@Param("organization") String organization);
}
