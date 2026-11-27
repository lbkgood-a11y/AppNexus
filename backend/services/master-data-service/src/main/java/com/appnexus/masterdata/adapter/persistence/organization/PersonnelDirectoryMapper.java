package com.appnexus.masterdata.adapter.persistence.organization;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 人员目录固定SQL映射；不接收任意SQL或客户端表名。 */
@Mapper
@PlatformScoped(tables={"identity_account","tenant_member","tenant_organization_position","tenant_organization_unit","tenant_person_leader","tenant_person_organization","tenant_person_position"})
public interface PersonnelDirectoryMapper {
 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT BIN_TO_UUID(id) id,name,enabled FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{p0}) ORDER BY code")
 List<Map<String,Object>> statement0(@Param("p0") Object p0);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(organization_id) organization_id,name,enabled FROM tenant_organization_position WHERE tenant_id=UUID_TO_BIN(#{p0}) ORDER BY code")
 List<Map<String,Object>> statement1(@Param("p0") Object p0);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT BIN_TO_UUID(m.id) member_id,BIN_TO_UUID(a.id) account_id,a.display_name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{p0}) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE ORDER BY member_id")
 List<Map<String,Object>> statement2(@Param("p0") Object p0);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT BIN_TO_UUID(m.id) member_id,BIN_TO_UUID(a.id) account_id,a.display_name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{p0}) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND m.id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT EXISTS(SELECT 1 FROM tenant_organization_unit u WHERE u.tenant_id=UUID_TO_BIN(#{p0}) AND u.id=UUID_TO_BIN(#{p1}) AND u.enabled=TRUE AND (EXISTS(SELECT 1 FROM tenant_person_organization po WHERE po.tenant_id=u.tenant_id AND po.organization_id=u.id AND po.person_id=UUID_TO_BIN(#{p2}) AND po.enabled=TRUE AND (po.starts_on IS NULL OR po.starts_on<=CURRENT_DATE) AND (po.ends_on IS NULL OR po.ends_on>=CURRENT_DATE)) OR EXISTS(SELECT 1 FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id WHERE a.tenant_id=u.tenant_id AND a.person_id=UUID_TO_BIN(#{p3}) AND p.organization_id=u.id AND p.enabled=TRUE AND a.enabled=TRUE AND a.starts_at<=CURRENT_TIMESTAMP(6) AND (a.ends_at IS NULL OR a.ends_at>CURRENT_TIMESTAMP(6)))))")
 Boolean statement4(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(organization_id) org,is_primary,starts_on,ends_on,enabled,remarks FROM tenant_person_organization WHERE tenant_id=UUID_TO_BIN(#{p0}) AND person_id=UUID_TO_BIN(#{p1})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="read")
 List<Map<String,Object>> statement5(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Select("SELECT BIN_TO_UUID(a.id) id,BIN_TO_UUID(a.position_id) pos,BIN_TO_UUID(p.organization_id) org,a.starts_at,a.ends_at,a.enabled,a.remarks FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id WHERE a.tenant_id=UUID_TO_BIN(#{p0}) AND a.person_id=UUID_TO_BIN(#{p1})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="read")
 List<Map<String,Object>> statement6(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Update("UPDATE tenant_person_organization SET is_primary=FALSE,version=version+1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND person_id=UUID_TO_BIN(#{p1}) AND is_primary=TRUE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions={"detail_create","detail_update"})
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Insert("INSERT INTO tenant_person_organization(tenant_id,id,person_id,organization_id,is_primary,starts_on,ends_on,enabled,remarks) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},NULLIF(#{p5},''),NULLIF(#{p6},''),#{p7},#{p8})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p2",actions="detail_create")
 int statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Update("UPDATE tenant_person_organization SET organization_id=UUID_TO_BIN(#{p0}),is_primary=#{p1},starts_on=NULLIF(#{p2},''),ends_on=NULLIF(#{p3},''),enabled=#{p4},remarks=#{p5},version=version+1 WHERE tenant_id=UUID_TO_BIN(#{p6}) AND person_id=UUID_TO_BIN(#{p7}) AND id=UUID_TO_BIN(#{p8})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p6",recordId="p7",actions="detail_update")
 int statement9(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Insert("INSERT INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at,ends_at,enabled,remarks) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},NULLIF(#{p5},''),#{p6},#{p7})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p2",actions="detail_create")
 int statement10(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Delete("DELETE l FROM tenant_person_leader l JOIN tenant_person_position a ON a.tenant_id=l.tenant_id AND a.id=l.assignment_id WHERE a.tenant_id=UUID_TO_BIN(#{p0}) AND a.person_id=UUID_TO_BIN(#{p1}) AND a.id=UUID_TO_BIN(#{p2})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="detail_update")
 int statement11(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Update("UPDATE tenant_person_position SET position_id=UUID_TO_BIN(#{p0}),starts_at=#{p1},ends_at=NULLIF(#{p2},''),enabled=#{p3},remarks=#{p4},version=version+1 WHERE tenant_id=UUID_TO_BIN(#{p5}) AND person_id=UUID_TO_BIN(#{p6}) AND id=UUID_TO_BIN(#{p7})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p5",recordId="p6",actions="detail_update")
 int statement12(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Delete("DELETE l FROM tenant_person_leader l JOIN tenant_person_position a ON a.tenant_id=l.tenant_id AND a.id=l.assignment_id WHERE a.tenant_id=UUID_TO_BIN(#{p0}) AND a.person_id=UUID_TO_BIN(#{p1}) AND a.id=UUID_TO_BIN(#{p2})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="detail_delete")
 int statement13(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */
 @Delete("DELETE FROM tenant_person_organization WHERE tenant_id=UUID_TO_BIN(#{p0}) AND person_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="p0",recordId="p1",actions="detail_delete")
 int statement14(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定删除岗位任职，不允许传入表名；调用方已清理负责人关系且处于同一事务。 */
 @Delete("DELETE FROM tenant_person_position WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND person_id=UUID_TO_BIN(#{person}) AND id=UUID_TO_BIN(#{id})")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.person",tenant="tenant",recordId="person",actions="detail_delete")
 int deletePosition(@Param("tenant") String tenant,@Param("person") String person,@Param("id") String id);

 /** @param tenant 已授权审计的可信租户
  * @param batch 固定的 UUID JSON 数组，许可与 SQL 绑定使用同一批次
  * @return 批量账号显示名称，允许历史成员且不返回其他账号资料 */
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="record-audit:resolve-actors",serviceKeys={"batch"})
 @Select("""
  SELECT BIN_TO_UUID(a.id) id,a.display_name
  FROM JSON_TABLE(#{batch}, '$[*]' COLUMNS (reference_id VARCHAR(36) PATH '$')) requested
  JOIN identity_account a ON a.id=UUID_TO_BIN(requested.reference_id)
  WHERE EXISTS (SELECT 1 FROM tenant_member m WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.account_id=a.id)
  """)
 List<Map<String,Object>> auditAccountNames(@Param("tenant") String tenant,@Param("batch") String batch);

 /** @param tenant 可信租户 UUID @param batch 类型及 UUID 的固定 JSON 数组 @return 当前批的组织和岗位名称 */
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",catalogPublication="organization.unit:read")
 @Select("""
  WITH requested AS (
    SELECT kind,reference_id FROM JSON_TABLE(#{batch}, '$[*]' COLUMNS (kind VARCHAR(16) PATH '$.kind', reference_id VARCHAR(36) PATH '$.id')) refs
  ), allowed AS (
    SELECT reference_id FROM JSON_TABLE(#{scope}, '$[*]' COLUMNS (reference_id VARCHAR(36) PATH '$')) scopes
  )
  SELECT r.kind,BIN_TO_UUID(o.id) id,o.name FROM requested r
  JOIN tenant_organization_unit o ON o.id=UUID_TO_BIN(r.reference_id) AND o.tenant_id=UUID_TO_BIN(#{tenant})
  WHERE r.kind='organization' AND (#{all} OR EXISTS (SELECT 1 FROM allowed a WHERE o.id=UUID_TO_BIN(a.reference_id)))
  UNION ALL
  SELECT r.kind,BIN_TO_UUID(p.id) id,p.name FROM requested r
  JOIN tenant_organization_position p ON p.id=UUID_TO_BIN(r.reference_id) AND p.tenant_id=UUID_TO_BIN(#{tenant})
  WHERE r.kind='position' AND (#{all} OR EXISTS (SELECT 1 FROM allowed a WHERE p.organization_id=UUID_TO_BIN(a.reference_id)))
  """)
 List<Map<String,Object>> organizationReferenceNames(@Param("tenant") String tenant,@Param("batch") String batch,
     @Param("all") boolean all,@Param("scope") String scope);

 /** @param actor 已认证的平台账号 UUID @param batch 当前批的账号 UUID JSON 数组
  * @return 仅平台审计读取权允许返回的显示名 */
 @com.appnexus.persistence.mybatis.GuardedScope(platformActor="actor",platformPermission="audit.entry:read")
 @Select("""
  SELECT BIN_TO_UUID(a.id) id,a.display_name
  FROM JSON_TABLE(#{batch}, '$[*]' COLUMNS (reference_id VARCHAR(36) PATH '$')) requested
  JOIN identity_account a ON a.id=UUID_TO_BIN(requested.reference_id)
  WHERE UUID_TO_BIN(#{actor}) IS NOT NULL
  """)
 List<Map<String,Object>> platformAuditAccountNames(@Param("actor") String actor,@Param("batch") String batch);

 /** @param tenant 可信租户 @param organization 已校验组织UUID @param kind POSITION或LEADER @param reference 岗位UUID，仅POSITION使用 @return 当前有效任职人员UUID，不授予档案权限 */
 @Select({"<script>","SELECT DISTINCT BIN_TO_UUID(a.person_id) FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id JOIN tenant_organization_unit u ON u.tenant_id=p.tenant_id AND u.id=p.organization_id",
 "<if test=\"kind == 'LEADER'\">JOIN tenant_person_leader l ON l.tenant_id=a.tenant_id AND l.assignment_id=a.id AND l.organization_id=u.id</if>",
 "WHERE a.tenant_id=UUID_TO_BIN(#{tenant}) AND u.id=UUID_TO_BIN(#{organization}) AND a.enabled=TRUE AND p.enabled=TRUE AND u.enabled=TRUE AND a.starts_at&lt;=CURRENT_TIMESTAMP(6) AND (a.ends_at IS NULL OR a.ends_at&gt;CURRENT_TIMESTAMP(6))",
 "<if test=\"kind == 'POSITION'\"> AND p.id=UUID_TO_BIN(#{reference})</if>","</script>"})
 List<String> resolve(@Param("tenant") String tenant,@Param("organization") String organization,@Param("kind") String kind,@Param("reference") String reference);
}
