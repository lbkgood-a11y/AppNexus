package com.appnexus.masterdata.adapter.jdbc;

import com.appnexus.masterdata.application.product.ProductRecords;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 商品父记录 MySQL 适配器；固定表与绑定租户参数，锁与 CAS 共享调用方事务。 */
public final class JdbcProductRecords implements ProductRecords {
 private final JdbcTemplate jdbc;
 /** @param jdbc 与商品扩展共用的 UTC 数据源，不创建独立事务 */
 public JdbcProductRecords(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public Map<String,Object> load(VerifiedTenantContext c,String id,boolean lock){
  var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,short_name shortName,description,brand,origin_text origin,BIN_TO_UUID(category_id) categoryId,BIN_TO_UUID(category_config_id) configId,product_form productForm,specification_mode specificationMode,BIN_TO_UUID(base_unit_id) unitId,status,structure_state structureState,draft_archived_at archivedAt,version,structure_revision structureRevision,legacy_enabled_snapshot legacyEnabled,legacy_specification_original legacySpecificationOriginal,legacy_specification_disposition legacyDisposition,barcode,legacy_unit_original legacyUnitOriginal,legacy_source_version legacySourceVersion,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),(r,n)->{
   var m=new LinkedHashMap<String,Object>();for(String col:List.of("id","code","name","shortName","description","brand","origin","categoryId","configId","productForm","specificationMode","unitId","status","structureState","legacySpecificationOriginal","legacyDisposition","barcode","legacyUnitOriginal","createdBy","updatedBy"))m.put(col,r.getString(col));
   for(String col:List.of("archivedAt","createdAt","updatedAt"))m.put(col,r.getTimestamp(col)==null?null:r.getTimestamp(col).toInstant().toString());
   m.put("legacySourceVersion",r.getObject("legacySourceVersion"));m.put("legacyEnabled",r.getObject("legacyEnabled"));m.put("version",r.getLong("version"));m.put("structureRevision",r.getLong("structureRevision"));return m;
  },c.tenantId().toString(),id);
  if(rows.isEmpty())throw new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");return rows.getFirst();
 }
 @Override public void advance(VerifiedTenantContext c,String id,long version){
  if(jdbc.update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",c.accountId().toString(),c.tenantId().toString(),id,version)!=1)throw new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");
 }
}
