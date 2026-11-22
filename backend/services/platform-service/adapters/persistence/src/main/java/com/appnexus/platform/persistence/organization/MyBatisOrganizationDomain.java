package com.appnexus.platform.persistence.organization;
import com.appnexus.platform.organization.OrganizationStructure;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.nio.ByteBuffer; import java.sql.Timestamp; import java.time.Instant; import java.util.*;
import org.springframework.stereotype.Repository; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.context.request.*;
/** 组织领域持久化适配器；跨聚合写入在事务中校验，租户标识不接受客户端输入。 */
@Repository public class MyBatisOrganizationDomain implements OrganizationStructure, com.appnexus.platform.authorization.DataAccessDeclaration.Executor {
 /** @return 组织领域实际执行统一租户及适用组织范围的资源 */
 @Override public java.util.Set<String> enforcedResources(){return java.util.Set.of("organization.type","organization.relation","organization.position", "organization.assignment");}
 private final OrganizationDomainMapper mapper;
 private final com.appnexus.platform.organization.PersonnelCatalog persons;
 private final com.appnexus.platform.authorization.AccessPlanning planning;
 private final com.appnexus.platform.authorization.RowAccessControl rows;
 private final com.appnexus.platform.authorization.ScopedWriteGate writes;
 /** @param mapper 组织固定存储映射 @param persons 人员公开端口 @param planning 实时统一权限计划 @param rows 公共范围执行器，不复制范围合并算法 @param writes 同事务组织写入许可 */
 public MyBatisOrganizationDomain(OrganizationDomainMapper mapper,com.appnexus.platform.organization.PersonnelCatalog persons,com.appnexus.platform.authorization.AccessPlanning planning,com.appnexus.platform.authorization.RowAccessControl rows,com.appnexus.platform.authorization.ScopedWriteGate writes){this.mapper=mapper;this.persons=persons;this.planning=planning;this.rows=rows;this.writes=writes;}
 public List<Type> types(){return mapper.types(tenant()).stream().map(r->new Type(id(r,"id"),s(r,"code"),s(r,"name"),b(r,"enabled"),n(r,"version"))).toList();}
 /** 在同一写事务确认现有全部组织范围，并将许可绑定到新类型 UUID。 */
 @Transactional public Type createType(String code,String name){
   var access=plan("create");
   requireTenantWideCreate(access);
   var id=UUID.randomUUID();
   writes.authorize(access,id);
   mapper.createType(tenant(),id.toString(),code,name);
   return new Type(id,code,name,true,0);
 }
 @Transactional public Type updateType(UUID id,String name,boolean enabled,long version){
   var access=plan("update");
   if(access.rows().type()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.RULES
       &&!rows.organizationRange(access,"organizationId").all())throw new SecurityException("ORGANIZATION_DATA_ACCESS_DENIED");
   var current=types().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(this::missing);if(!enabled&&mapper.typeUsage(tenant(),id.toString())>0)throw new IllegalStateException("TYPE_IN_USE");writes.authorize(access,id);if(mapper.updateType(tenant(),id.toString(),name,enabled,version)!=1)throw conflict();return new Type(id,current.code(),name,enabled,version+1);}
 public List<Unit> units(){var range=range("read");return mapper.scopedUnits(tenant(),range.all(),range.ids()).stream().map(r->new Unit(id(r,"id"),s(r,"code"),id(r,"typeId"),nullableId(r,"parentId"),s(r,"name"),b(r,"enabled"),n(r,"version"))).toList();}
 /**
  * @param code 本租户固定组织编码
  * @param typeId 必须启用且与目标上级同类型
  * @param parentId 目标上级；空值表示新根，仅固定全部范围允许
  * @param name 新组织名称
  * @return 已提交的组织记录
  */
 @Transactional public Unit createUnit(String code,UUID typeId,UUID parentId,String name){
   // 与组织移动、编辑共用锁序，在校验及插入期间保持目标层级不变。
   mapper.lockUnits(tenant());
   var access=plan("create");
   if(parentId==null)requireTenantWideCreate(access);
   else requireOrganization(rows.organizationRange(access,"parentOrganizationId"),parentId);
   validateParent(null,typeId,parentId);
   var id=UUID.randomUUID();
   writes.authorize(access,id,parentId);
   mapper.createUnit(tenant(),id.toString(),code,typeId.toString(),text(parentId),name);
   return new Unit(id,code,typeId,parentId,name,true,0);
 }
 private void requireTenantWideCreate(com.appnexus.platform.authorization.AccessPlan access){
   if(access.rows().type()!=com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL)
      throw new SecurityException("ORGANIZATION_CREATE_SCOPE_REQUIRED");
 }
 @Transactional public Unit updateUnit(UUID id,String name,boolean enabled,long version){mapper.lockUnits(tenant());var access=plan("update");requireOrganization(rows.organizationRange(access,"organizationId"),id);var current=unit(id);if(!enabled){var impact=rawImpact(id);if(impact.children()>0||impact.relations()>0||impact.positions()>0||impact.activeAssignments()>0||impact.leaders()>0)throw new IllegalStateException("ORGANIZATION_IN_USE");}writes.authorize(access,id);if(mapper.updateUnit(tenant(),id.toString(),name,enabled,version)!=1)throw conflict();return new Unit(id,current.code(),current.typeId(),current.parentId(),name,enabled,version+1);}
 /**
  * 同一个事务及同一更新计划验证源与目标，防止组织树变化或先后解析导致授权不一致。
  * @param id 被移动的组织 UUID
  * @param parentId 目标上级 UUID；动态受限范围不可提升为根组织
  * @param version 源组织乐观锁版本
  * @return 更新后的组织
  */
 @Transactional public Unit moveUnit(UUID id,UUID parentId,long version){
   mapper.lockUnits(tenant());
   var access=plan("update");
   var range=rows.organizationRange(access,"organizationId");
   requireOrganization(range,id);
   if(parentId!=null)requireOrganization(range,parentId);
   else if(access.rows().type()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.RULES&&!range.all())
      throw new SecurityException("ORGANIZATION_DATA_ACCESS_DENIED");
   var current=unit(id);validateParent(id,current.typeId(),parentId);for(UUID cursor=parentId;cursor!=null;){if(cursor.equals(id))throw new IllegalArgumentException("ORGANIZATION_CYCLE");cursor=unit(cursor).parentId();}writes.authorize(access,id,parentId);if(mapper.moveUnit(tenant(),id.toString(),text(parentId),version)!=1)throw conflict();return new Unit(id,current.code(),current.typeId(),parentId,current.name(),current.enabled(),version+1);}
 public Impact impact(UUID id){requireUnit("read",id);return rawImpact(id);}
 private Impact rawImpact(UUID id){unit(id);var r=mapper.impact(tenant(),id.toString());return new Impact(i(r,"children"),i(r,"relations"),i(r,"positions"),i(r,"activeAssignments"),i(r,"leaders"));}
 public List<Relation> relations(){return mapper.relations(tenant(),range("read"),null).stream().map(r->new Relation(id(r,"id"),s(r,"relationType"),id(r,"sourceId"),id(r,"targetId"),n(r,"version"))).toList();}
 @Transactional public Relation createRelation(String kind,UUID source,UUID target){if(source.equals(target))throw new IllegalArgumentException("SELF_RELATION");var access=plan("create");requireOrganization(access,source);requireOrganization(access,target);unit(source);unit(target);var id=UUID.randomUUID();writes.authorizeChild(access,"organization.relation","create",id,List.of(source.toString(),target.toString()));mapper.createRelation(tenant(),id.toString(),kind,source.toString(),target.toString());return new Relation(id,kind,source,target,0);}
 @Transactional public void deleteRelation(UUID id,long version){var access=plan("delete");if(mapper.relations(tenant(),rows.organizationRange(access,"organizationId"),id.toString()).isEmpty())throw missing();writes.authorizeChild(access,"organization.relation","delete",id,List.of());if(mapper.deleteRelation(tenant(),id.toString(),version)!=1)throw conflict();}
 public List<Position> positions(){return mapper.positions(tenant(),range("read"),null).stream().map(MyBatisOrganizationDomain::position).toList();}
 @Transactional public Position createPosition(UUID organizationId,String code,String name){var access=plan("create");requireOrganization(rows.organizationRange(access,"parentOrganizationId"),organizationId);if(!unit(organizationId).enabled())throw new IllegalArgumentException("ORGANIZATION_DISABLED");var id=UUID.randomUUID();writes.authorizeChild(access,"organization.position","create",id,List.of(organizationId.toString()));mapper.createPosition(tenant(),id.toString(),organizationId.toString(),code,name);return new Position(id,organizationId,code,name,true,0);}
 @Transactional public Position updatePosition(UUID id,String name,boolean enabled,long version){var access=plan("update");var p=mapper.positions(tenant(),rows.organizationRange(access,"organizationId"),id.toString()).stream().map(MyBatisOrganizationDomain::position).findFirst().orElseThrow(this::missing);if(!enabled&&mapper.positionUsage(tenant(),id.toString())>0)throw new IllegalStateException("POSITION_IN_USE");writes.authorizeChild(access,"organization.position","update",id,List.of());if(mapper.updatePosition(tenant(),id.toString(),name,enabled,version)!=1)throw conflict();return new Position(id,p.organizationId(),p.code(),name,enabled,version+1);}
 public List<Assignment> assignments(){return mapper.assignments(tenant(),range("read"),null).stream().map(MyBatisOrganizationDomain::assignment).toList();}
 @Transactional public Assignment createAssignment(UUID member,UUID position,Instant starts,Instant ends){var access=plan("create");if(mapper.positions(tenant(),rows.organizationRange(access,"parentOrganizationId"),position.toString()).isEmpty())throw missing();if(persons().stream().noneMatch(p->p.id().equals(member)))throw new IllegalArgumentException("PERSON_OR_POSITION_UNAVAILABLE");if(ends!=null&&!ends.isAfter(starts))throw new IllegalArgumentException("INVALID_ASSIGNMENT_PERIOD");var id=UUID.randomUUID();writes.authorizeChild(access,"organization.assignment","create",id,List.of(member.toString(),position.toString()));touch(access,member);if(mapper.createAssignment(tenant(),id.toString(),member.toString(),position.toString(),starts,ends)!=1)throw new IllegalArgumentException("MEMBER_OR_POSITION_UNAVAILABLE");return new Assignment(id,member,position,starts,ends,true,0);}
 @Transactional public Assignment updateAssignment(UUID id,Instant ends,boolean enabled,long version){var access=plan("update");var a=mapper.assignments(tenant(),rows.organizationRange(access,"organizationId"),id.toString()).stream().map(MyBatisOrganizationDomain::assignment).findFirst().orElseThrow(this::missing);if(ends!=null&&!ends.isAfter(a.startsAt()))throw new IllegalArgumentException("INVALID_ASSIGNMENT_PERIOD");writes.authorizeChild(access,"organization.assignment","update",id,List.of());touch(access,a.personId());if(mapper.updateAssignment(tenant(),id.toString(),ends,enabled,version)!=1)throw conflict();if(!enabled||(ends!=null&&!ends.isAfter(Instant.now())))mapper.removeAssignmentLeaders(tenant(),id.toString());return new Assignment(id,a.personId(),a.positionId(),a.startsAt(),ends,enabled,version+1);}
 public List<Leader> leaders(){return mapper.leaders(tenant(),range("read")).stream().map(r->new Leader(id(r,"organizationId"),id(r,"assignmentId"))).toList();}
 @Transactional public void setLeader(UUID organization,UUID assignment,boolean selected){var access=plan("update");requireOrganization(rows.organizationRange(access,"organizationId"),organization);writes.authorize(access,organization,assignment);int changed=selected?mapper.addLeader(tenant(),organization.toString(),assignment.toString()):mapper.removeLeader(tenant(),organization.toString(),assignment.toString());if(changed!=1)throw new IllegalArgumentException("LEADER_ASSIGNMENT_UNAVAILABLE");}
 public List<Person> persons(){var bytes=java.nio.ByteBuffer.wrap(tenant());var tenantId=new UUID(bytes.getLong(),bytes.getLong());return persons.active(tenantId).stream().map(p->new Person(p.id(),p.displayName())).toList();}
 /** 使用当前可信实际账号更新人员审计，不将任职人员冒充操作者。 */
 private void touch(com.appnexus.platform.authorization.AccessPlan access,UUID person){
   var c=context();
   writes.authorizeChild(access,"organization.assignment.person-audit","touch",person,List.of(c.accountId().toString()));
   persons.touch(c.tenantId(),person,c.accountId());
 }
 private void validateParent(UUID self,UUID type,UUID parent){if(types().stream().noneMatch(v->v.id().equals(type)&&v.enabled()))throw new IllegalArgumentException("TYPE_UNAVAILABLE");if(parent!=null){var p=unit(parent);if(!p.typeId().equals(type)||!p.enabled()||parent.equals(self))throw new IllegalArgumentException("PARENT_UNAVAILABLE");}}
 /** 仅供已准入写用例检查父链及约束，不能作为对外读取入口；不能因缺少read而阻断合法update。 */
 private Unit unit(UUID id){return mapper.units(tenant(),id.toString()).stream().map(r->new Unit(id(r,"id"),s(r,"code"),id(r,"typeId"),nullableId(r,"parentId"),s(r,"name"),b(r,"enabled"),n(r,"version"))).findFirst().orElseThrow(this::missing);}
 /** 每个操作独立解析，不复用read权作为update准入；缺策略及未知范围由公共执行器拒绝。 */
 private com.appnexus.platform.authorization.AccessPlan plan(String action){var c=context();return planning.plan(new com.appnexus.platform.authorization.AccessAuthorization.Subject(c.accountId(),c.tenantId(),c.memberId(),c.personId(),c.organizationId()),"organization.unit:"+action,"organization.unit");}
 private void requireUnit(String action,UUID id){
   requireOrganization(range(action),id);
 }
 private void requireOrganization(com.appnexus.platform.authorization.RowAccessControl.OrganizationRange range,UUID id){
   if(!range.all()&&!range.ids().contains(id.toString()))throw new SecurityException("ORGANIZATION_DATA_ACCESS_DENIED");
 }
 /** 关联操作继承组织单元的同操作范围；授权中心继续通过一个组织策略管理该范围。 */
 private com.appnexus.platform.authorization.RowAccessControl.OrganizationRange range(String action){return rows.organizationRange(plan(action),"create".equals(action)?"parentOrganizationId":"organizationId");}
 private void requireOrganization(com.appnexus.platform.authorization.AccessPlan access,UUID id){if(!rows.allowsOrganization(access,id))throw new SecurityException("ORGANIZATION_DATA_ACCESS_DENIED");}
 private static Position position(Map<String,Object> r){return new Position(id(r,"id"),id(r,"organizationId"),s(r,"code"),s(r,"name"),b(r,"enabled"),n(r,"version"));}
 private static Assignment assignment(Map<String,Object> r){return new Assignment(id(r,"id"),id(r,"personId"),id(r,"positionId"),instant(r,"startsAt"),nullableInstant(r,"endsAt"),b(r,"enabled"),n(r,"version"));}
 private VerifiedTenantContext context(){var attributes=RequestContextHolder.getRequestAttributes();if(!(attributes instanceof ServletRequestAttributes request)||!(request.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))throw new SecurityException("Verified tenant context required");return c;} private IllegalStateException conflict(){return new IllegalStateException("CONCURRENT_MODIFICATION");} private NoSuchElementException missing(){return new NoSuchElementException("ORGANIZATION_OBJECT_NOT_FOUND");}
 private byte[] tenant(){var a=RequestContextHolder.getRequestAttributes();if(!(a instanceof ServletRequestAttributes s)||!(s.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))throw new IllegalStateException("Verified tenant context required");return ByteBuffer.allocate(16).putLong(c.tenantId().getMostSignificantBits()).putLong(c.tenantId().getLeastSignificantBits()).array();}
 private static String s(Map<String,Object> r,String k){return String.valueOf(r.get(k));} private static UUID id(Map<String,Object> r,String k){return UUID.fromString(s(r,k));} private static UUID nullableId(Map<String,Object> r,String k){return r.get(k)==null?null:id(r,k);} private static long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();} private static int i(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();} private static boolean b(Map<String,Object> r,String k){Object v=r.get(k);return Boolean.TRUE.equals(v)||(v instanceof Number x&&x.intValue()!=0);} private static String text(UUID id){return id==null?null:id.toString();} private static Instant instant(Map<String,Object> r,String k){Object v=r.get(k);return v instanceof Timestamp t?t.toInstant():(Instant)v;} private static Instant nullableInstant(Map<String,Object> r,String k){return r.get(k)==null?null:instant(r,k);}
}
