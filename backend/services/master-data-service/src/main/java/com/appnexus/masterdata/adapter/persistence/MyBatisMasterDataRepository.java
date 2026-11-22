package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.api.masterdata.MasterData.*;
import com.appnexus.masterdata.adapter.persistence.*;
import com.appnexus.masterdata.application.masterdata.MasterDataRepository;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
/** 主数据存储端口MyBatis适配；固定枚举映射，版本、审计与原聚合事务保持。 */
public final class MyBatisMasterDataRepository implements MasterDataRepository {
 private final MasterRecordMapper mapper;
 private final AccessAuthorization access;
 private final ScopedWriteGate writes;
 /** @param mapper 固定主数据映射 @param access 原地理目录操作准入 @param writes 同数据源事务门禁 */
 public MyBatisMasterDataRepository(MasterRecordMapper mapper,AccessAuthorization access,ScopedWriteGate writes){
  this.mapper=Objects.requireNonNull(mapper);
  this.access=access;
  this.writes=writes;
 }
 /** 地理目录沿用原功能权限，事务许可限定可信租户和目标档案。审计只能沿用成功的维护操作。 */
 private void authorizeGeography(VerifiedTenantContext c,Kind kind,String id,String action){
  if(kind!=Kind.COUNTRY_REGION&&kind!=Kind.SUBDIVISION)return;
  var subject=AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());
  String permission=kind.resource()+":"+action;
  writes.authorizeServiceOperation(c.tenantId(),permission,List.of(id),()->access.hasPermission(subject,permission));
  if(!action.equals("create")){
   writes.authorizeServiceOperation(c.tenantId(),kind.resource()+":audit",List.of(id),()->access.hasPermission(subject,permission));
  }
 }
 @Override public void create(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){authorizeGeography(c,kind,id,"create");mapper.create(c.tenantId().toString(),kind,id,input,values,c.accountId().toString());}
 @Override public void update(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){authorizeGeography(c,kind,id,"update");if(mapper.update(c.tenantId().toString(),kind,id,input,values)!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");}
 @Override public void status(VerifiedTenantContext c,Kind kind,String id,Status input){authorizeGeography(c,kind,id,"set_status");if(mapper.status(c.tenantId().toString(),kind,id,input.enabled(),input.version())!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");}
 @Override public void organization(VerifiedTenantContext c,String id,String organization){mapper.organization(c.tenantId().toString(),id,organization);}
 @Override public Map<String,String> previous(VerifiedTenantContext c,Kind kind,String id){var rows=mapper.previous(c.tenantId().toString(),kind,id);return rows.isEmpty()?Map.of():rows.getFirst();}
 @Override public void audit(VerifiedTenantContext c,Kind kind,String id){mapper.audit(c.tenantId().toString(),kind,id,c.accountId().toString());}
}
