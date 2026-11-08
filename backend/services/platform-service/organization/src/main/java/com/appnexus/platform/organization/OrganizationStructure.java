package com.appnexus.platform.organization;

import java.time.Instant;
import java.util.*;

/** 组织领域应用端口；租户身份由适配器获取，版本号用于拒绝并发覆盖。 */
public interface OrganizationStructure {
    record Type(UUID id,String code,String name,boolean enabled,long version) {}
    record Unit(UUID id,String code,UUID typeId,UUID parentId,String name,boolean enabled,long version) {}
    record Relation(UUID id,String relationType,UUID sourceId,UUID targetId,long version) {}
    record Position(UUID id,UUID organizationId,String code,String name,boolean enabled,long version) {}
    record Assignment(UUID id,UUID memberId,UUID positionId,Instant startsAt,Instant endsAt,boolean enabled,long version) {}
    record Leader(UUID organizationId,UUID assignmentId) {}
    record Member(UUID id,String displayName) {}
    record Impact(int children,int relations,int positions,int activeAssignments,int leaders) {}
    List<Type> types(); Type createType(String code,String name); Type updateType(UUID id,String name,boolean enabled,long version);
    List<Unit> units(); Unit createUnit(String code,UUID typeId,UUID parentId,String name); Unit updateUnit(UUID id,String name,boolean enabled,long version); Unit moveUnit(UUID id,UUID parentId,long version); Impact impact(UUID id);
    List<Relation> relations(); Relation createRelation(String relationType,UUID sourceId,UUID targetId); void deleteRelation(UUID id,long version);
    List<Position> positions(); Position createPosition(UUID organizationId,String code,String name); Position updatePosition(UUID id,String name,boolean enabled,long version);
    List<Assignment> assignments(); Assignment createAssignment(UUID memberId,UUID positionId,Instant startsAt,Instant endsAt); Assignment updateAssignment(UUID id,Instant endsAt,boolean enabled,long version);
    List<Leader> leaders(); void setLeader(UUID organizationId,UUID assignmentId,boolean selected);
    List<Member> members();
}
