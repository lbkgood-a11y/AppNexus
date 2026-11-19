package com.appnexus.masterdata.adapter.jdbc;

import com.appnexus.masterdata.application.MasterDataRecords;
import com.appnexus.masterdata.MasterData.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 固定主数据表持久化适配器；Kind 控制表列白名单，业务值均绑定参数，不接收任意 SQL。 */
public final class JdbcMasterDataRecords implements MasterDataRecords {
  private final JdbcTemplate jdbc;
  /** @param jdbc 与用例事务共用的 UTC 数据源；不能使用独立自动提交连接 */
  public JdbcMasterDataRecords(JdbcTemplate jdbc){this.jdbc=jdbc;}
  @Override public void create(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){
    var args=new ArrayList<Object>(List.of(id,c.tenantId().toString(),input.code().trim(),input.name().trim()));args.addAll(values.values());args.add(c.accountId().toString());args.add(c.accountId().toString());
    jdbc.update("INSERT INTO "+kind.table()+"(id,tenant_id,code,name,"+String.join(",",kind.fields().stream().map(kind::column).toList())+",created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,"+String.join(",",Collections.nCopies(kind.fields().size(),"?"))+",UUID_TO_BIN(?),CURRENT_TIMESTAMP(6),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6))",args.toArray());
  }
  @Override public void update(VerifiedTenantContext c,Kind kind,String id,Input input,Map<String,String> values){
    var args=new ArrayList<Object>(List.of(input.name().trim()));args.addAll(values.values());args.add(c.tenantId().toString());args.add(id);args.add(input.version());args.add(input.code().trim());
    if(jdbc.update("UPDATE "+kind.table()+" SET name=?,"+String.join(",",kind.fields().stream().map(f->kind.column(f)+"=?").toList())+",version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=? AND code=?",args.toArray())!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");
  }
  @Override public void status(VerifiedTenantContext c,Kind kind,String id,Status input){
    if(jdbc.update("UPDATE "+kind.table()+" SET enabled=?,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",input.enabled(),c.tenantId().toString(),id,input.version())!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");
  }
  @Override public void organization(VerifiedTenantContext c,String id,String organizationId){jdbc.update("UPDATE md_partner SET organization_id=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",organizationId,c.tenantId().toString(),id);}
  @Override public Map<String,String> previous(VerifiedTenantContext c,Kind kind,String id){
    var fields=kind.fields();var rows=jdbc.query("SELECT "+String.join(",",fields.stream().map(kind::column).toList())+" FROM "+kind.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",(r,n)->{var result=new HashMap<String,String>();for(String field:fields)result.put(field,r.getString(kind.column(field)));return result;},c.tenantId().toString(),id);
    return rows.isEmpty()?Map.of():rows.getFirst();
  }
  @Override public void audit(VerifiedTenantContext c,Kind kind,String id){jdbc.update("UPDATE "+kind.table()+" SET updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.accountId().toString(),c.tenantId().toString(),id);}
}
