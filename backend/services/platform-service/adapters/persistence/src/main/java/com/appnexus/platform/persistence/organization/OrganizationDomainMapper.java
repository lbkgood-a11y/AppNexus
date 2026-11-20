package com.appnexus.platform.persistence.organization;

import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 组织领域受控 SQL；所有方法都显式绑定已验证租户，执行门禁复核表白名单。 */
@TenantAggregateScoped(tables={"tenant_organization_type","tenant_organization_unit","tenant_organization_relation","tenant_organization_position","tenant_person_position","tenant_person_leader"},statements={"types","createType","updateType","typeUsage","units","scopedUnits","lockUnits","createUnit","updateUnit","moveUnit","impact","relations","createRelation","deleteRelation","positions","createPosition","updatePosition","positionUsage","assignments","createAssignment","updateAssignment","removeAssignmentLeaders","leaders","addLeader","removeLeader"})
@Mapper
public interface OrganizationDomainMapper {
 List<Map<String,Object>> types(@Param("__verifiedTenant") byte[] tenant);
 int createType(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("code") String code,@Param("name") String name);
 int updateType(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("name") String name,@Param("enabled") boolean enabled,@Param("version") long version);
 int typeUsage(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 List<Map<String,Object>> units(@Param("__verifiedTenant") byte[] tenant);
 /** @param tenant 可信租户二进制UUID @param all 已授权ALL范围 @param ids 解析后的组织UUID集合；非ALL空集必须拒绝 @return 在SQL中完成行范围过滤的组织列表 */
 List<Map<String,Object>> scopedUnits(@Param("__verifiedTenant") byte[] tenant,@Param("all") boolean all,@Param("ids") List<String> ids);
 List<String> lockUnits(@Param("__verifiedTenant") byte[] tenant);
 int createUnit(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("code") String code,@Param("typeId") String typeId,@Param("parentId") String parentId,@Param("name") String name);
 int updateUnit(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("name") String name,@Param("enabled") boolean enabled,@Param("version") long version);
 int moveUnit(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("parentId") String parentId,@Param("version") long version);
 Map<String,Object> impact(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 List<Map<String,Object>> relations(@Param("__verifiedTenant") byte[] tenant);
 int createRelation(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("relationType") String relationType,@Param("sourceId") String sourceId,@Param("targetId") String targetId);
 int deleteRelation(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("version") long version);
 List<Map<String,Object>> positions(@Param("__verifiedTenant") byte[] tenant);
 int createPosition(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("organizationId") String organizationId,@Param("code") String code,@Param("name") String name);
 int updatePosition(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("name") String name,@Param("enabled") boolean enabled,@Param("version") long version);
 int positionUsage(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 List<Map<String,Object>> assignments(@Param("__verifiedTenant") byte[] tenant);
 int createAssignment(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("personId") String personId,@Param("positionId") String positionId,@Param("startsAt") Instant startsAt,@Param("endsAt") Instant endsAt);
 int updateAssignment(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("endsAt") Instant endsAt,@Param("enabled") boolean enabled,@Param("version") long version);
 int removeAssignmentLeaders(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
 List<Map<String,Object>> leaders(@Param("__verifiedTenant") byte[] tenant);
 int addLeader(@Param("__verifiedTenant") byte[] tenant,@Param("organizationId") String organizationId,@Param("assignmentId") String assignmentId);
 int removeLeader(@Param("__verifiedTenant") byte[] tenant,@Param("organizationId") String organizationId,@Param("assignmentId") String assignmentId);
}
