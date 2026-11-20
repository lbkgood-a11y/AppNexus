package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.masterdata.application.MasterDataRecords;
import com.appnexus.masterdata.MasterData.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
/** 主数据存储端口MyBatis适配；固定枚举映射，版本、审计与原聚合事务保持。 */
public final class MyBatisMasterDataRecords implements MasterDataRecords {
 private final MasterRecordMapper mapper;
 /** @param mapper 同数据源及事务的固定主数据Mapper */
 public MyBatisMasterDataRecords(MasterRecordMapper mapper){this.mapper=Objects.requireNonNull(mapper);}
 @Override public void create(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){mapper.create(c.tenantId().toString(),kind,id,input,values,c.accountId().toString());}
 @Override public void update(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){if(mapper.update(c.tenantId().toString(),kind,id,input,values)!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");}
 @Override public void status(VerifiedTenantContext c,Kind kind,String id,Status input){if(mapper.status(c.tenantId().toString(),kind,id,input.enabled(),input.version())!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");}
 @Override public void organization(VerifiedTenantContext c,String id,String organization){mapper.organization(c.tenantId().toString(),id,organization);}
 @Override public Map<String,String> previous(VerifiedTenantContext c,Kind kind,String id){var rows=mapper.previous(c.tenantId().toString(),kind,id);return rows.isEmpty()?Map.of():rows.getFirst();}
 @Override public void audit(VerifiedTenantContext c,Kind kind,String id){mapper.audit(c.tenantId().toString(),kind,id,c.accountId().toString());}
}
