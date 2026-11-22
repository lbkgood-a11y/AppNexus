package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.organization.PersonData.Input;
import com.appnexus.platform.organization.port.personnel.PersonRepository;
import org.springframework.stereotype.Component;
/** 人员固定存储适配；SQL留在Mapper，驱动时间在边界转换，写入参与原用例事务。 */
@Component
public final class MyBatisPersonRepository implements PersonRepository, com.appnexus.platform.authorization.DataAccessDeclaration.Executor {
 /** @return 由原权限用例和该适配器的固定数据边界共同约束的资源编码 */
 @Override public java.util.Set<String> enforcedResources(){return java.util.Set.of("masterdata.person");}
 private static final String RESOURCE="masterdata.person";
 private static final RowAccessControl.Column ID=new RowAccessControl.Column("id",DataGrantRules.ValueType.UUID);
 private static final RowAccessControl.Mapping MAPPING=new RowAccessControl.Mapping(RESOURCE,ID,ID,Map.of("personId",ID));
 private final PersonRecordMapper mapper;
 private final StandardCrudScope scope;
 private final ScopedWriteGate writes;
 private final AccessAuthorization authorization;
 /**
  * @param mapper 固定人员 SQL 映射
  * @param planning 实时统一权限计划，不缓存用户身份
  * @param rows 公共范围编译器
  * @param references 公共组织目录，由标准范围入口装配
  * @param authorization 新增沿用原功能权限判定
  * @param writes 仅当前事务有效的主档写入许可
  */
 public MyBatisPersonRepository(PersonRecordMapper mapper,AccessPlanning planning,RowAccessControl rows,
     DataAccessReferences references,ScopedWriteGate writes,AccessAuthorization authorization){
   this.mapper=mapper;
   this.scope=new StandardCrudScope(planning,rows,references,MAPPING);
   this.writes=writes;
   this.authorization=authorization;
 }
 /** {@inheritDoc} */
 public PersonPage page(VerifiedTenantContext context,String keyword,Boolean enabled,int size,int offset){
   var tenant=context.tenantId().toString();
   var predicate=readPredicate(context);
   return new PersonPage(PersonnelStorageValues.stable(mapper.list(tenant,keyword,enabled,size,offset,predicate)),
       mapper.count(tenant,keyword,enabled,predicate));
 }
 /** {@inheritDoc} */
 @Override public Map<String,String> names(VerifiedTenantContext context,List<String> ids){
   var result=new java.util.HashMap<String,String>();
   RowAccessControl.Predicate predicate;
   try{predicate=readPredicate(context);}catch(SecurityException denied){return Map.of();}
   for(var row:mapper.names(context.tenantId().toString(),ids,predicate)){
     result.put((String)row.get("id"),(String)row.get("name"));
   }
   return Map.copyOf(result);
 }
 /** 人员档案 ID 即业务人员 ID；记录选择和动态条件均由统一执行器编译。 */
 RowAccessControl.Predicate readPredicate(VerifiedTenantContext context){return scope.predicate(plan(context,"read"));}
 private AccessPlan plan(VerifiedTenantContext context,String action){
   var subject=new AccessAuthorization.Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
   return scope.plan(subject,action);
 }
 /** {@inheritDoc} */
 public int create(VerifiedTenantContext context,String id,Input input,Map<String,String> values){
   String permission=RESOURCE+":create";
   var subject=AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId());
   writes.authorizeServiceOperation(context.tenantId(),permission,List.of(id),
       ()->authorization.hasPermission(subject,permission));
   return mapper.create(context.tenantId().toString(),id,input,values,context.accountId().toString());
 }
 /** 仅在受控详情查询已返回父档案后，签发同事务、同父 UUID 的明细读取许可。 */
 void authorizeProfileDetails(RowAccessControl.Predicate predicate,String id){
   writes.authorize(predicate.plan(),UUID.fromString(id));
 }
 /** {@inheritDoc} */
 public void lockForWrite(VerifiedTenantContext context,String id,String action){
   lockForWrite(context,id,action,false);
 }
 /** 已有全量明细权限访问不存在的父档案时，保留原版本冲突响应。 */
 void lockForWrite(VerifiedTenantContext context,String id,String action,boolean preserveMissingConflict){
   var plan=lock(context,id,action,preserveMissingConflict);
   writes.authorize(plan,UUID.fromString(id));
 }
 private AccessPlan lock(VerifiedTenantContext context,String id,String action,boolean preserveMissingConflict){
   var plan=plan(context,action);
   var access=scope.predicate(plan);
   if(mapper.lockForWrite(context.tenantId().toString(),id,access).size()==1)return plan;
   if(preserveMissingConflict&&access.sql().isEmpty())
     throw new com.appnexus.platform.organization.PersonData.Rejection(409,"MASTER_DATA_CHANGED");
   throw new com.appnexus.platform.organization.PersonData.Rejection(403,"MASTER_DATA_DENIED");
 }
 /** {@inheritDoc} */
 public int update( String tenant, String id, Input input, Map<String,String> values){return mapper.update(tenant,id,input,values);}
 /** {@inheritDoc} */
 public int status( String tenant, String id, boolean enabled, long version){return mapper.status(tenant,id,enabled,version);}
 /** {@inheritDoc} */
 public List<Map<String,String>> previous( String tenant, String id){return PersonnelStorageValues.stable(mapper.previous(tenant,id));}
 /** {@inheritDoc} */
 public int audit( String tenant, String id, String actor){return mapper.audit(tenant,id,actor);}
}
