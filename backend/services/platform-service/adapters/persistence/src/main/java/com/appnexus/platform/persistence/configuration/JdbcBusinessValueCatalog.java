package com.appnexus.platform.persistence.configuration;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.appnexus.platform.configuration.DictionaryValueStandard;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
/** 字典表仅由配置适配器访问；领域模块依赖公开契约，不跨模块直接查询配置表。 */
@Service
public final class JdbcBusinessValueCatalog implements BusinessValueCatalog {
 private final JdbcTemplate jdbc;
 /** @param jdbc 复用宿主事务与 UTC 数据源 */
 public JdbcBusinessValueCatalog(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public Map<String,List<Option>> options(UUID tenant,Map<String,String> bindings){
  Objects.requireNonNull(tenant);var result=new LinkedHashMap<String,List<Option>>();var cache=new HashMap<String,List<Option>>();
  // 历史项即使被旧版本误设为启用也仅用于解析，不能绕过统一标准成为新选项。
  for(var binding:bindings.entrySet())result.put(binding.getKey(),cache.computeIfAbsent(binding.getValue(),code->jdbc.query("SELECT i.item_value,i.name,(d.status='ENABLED' AND i.status='ENABLED') enabled,i.code FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(?) AND d.code=? AND d.deleted_at IS NULL AND i.deleted_at IS NULL ORDER BY i.sort_order,i.id",(r,n)->new Option(r.getString(1),r.getString(2),r.getBoolean(3)&&DictionaryValueStandard.validValue(r.getString(1))&&DictionaryValueStandard.validCode(r.getString(4))),tenant.toString(),code)));
  return result;
 }
 @Override public boolean reference(UUID tenant,UUID actor,String consumer,String reference,Map<String,String> bindings,Map<String,String> values,Map<String,String> previous){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("DICTIONARY_REFERENCE_REQUIRES_TRANSACTION");
  // 按字典编码排序加父锁，与字典维护共用锁，避免校验与并发停用/删除之间产生竞态。
  var locked=new HashMap<String,Map<String,Object>>();
  for(String code:new TreeSet<>(bindings.values())){var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,status FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code=? AND deleted_at IS NULL FOR UPDATE",tenant.toString(),code);if(!rows.isEmpty())locked.put(code,rows.getFirst());}
  for(var binding:bindings.entrySet()){
   String value=values.getOrDefault(binding.getKey(),"").trim();if(value.isEmpty())continue;
   boolean unchanged=value.equals(previous.getOrDefault(binding.getKey(),""));var dictionary=locked.get(binding.getValue());
   if(dictionary==null){if(unchanged)continue;return false;}
   var items=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,status,code FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) AND dictionary_id=UUID_TO_BIN(?) AND item_value=? AND deleted_at IS NULL",tenant.toString(),dictionary.get("id"),value);
   if(items.isEmpty()){if(unchanged)continue;return false;}
   if(!unchanged&&(!DictionaryValueStandard.validValue(value)||!DictionaryValueStandard.validCode((String)items.getFirst().get("code"))||!"ENABLED".equals(dictionary.get("status"))||!"ENABLED".equals(items.getFirst().get("status"))))return false;
   // 引用保留历史，字段后续改值也不删除旧引用；配置删除不能破坏历史业务含义。
   jdbc.update("INSERT INTO tenant_dictionary_item_reference(tenant_id,item_id,consumer,reference_key,created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),CURRENT_TIMESTAMP(6),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE reference_key=reference_key",tenant.toString(),items.getFirst().get("id"),consumer,reference,actor==null?null:actor.toString(),actor==null?null:actor.toString());
  }
  return true;
 }
}
