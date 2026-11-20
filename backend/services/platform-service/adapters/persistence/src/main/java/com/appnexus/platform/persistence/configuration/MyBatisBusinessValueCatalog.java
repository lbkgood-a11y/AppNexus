package com.appnexus.platform.persistence.configuration;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.appnexus.platform.configuration.DictionaryValueStandard;
import org.springframework.stereotype.Service;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
/** 字典表仅由配置适配器访问；领域模块依赖公开契约，不跨模块直接查询配置表。 */
@Service
public final class MyBatisBusinessValueCatalog implements BusinessValueCatalog {
 private final BusinessValueMapper mapper;
 /** @param mapper 固定字典引用 Mapper，复用宿主事务与 UTC 数据源 */
 public MyBatisBusinessValueCatalog(BusinessValueMapper mapper){this.mapper=Objects.requireNonNull(mapper);}
 @Override public Map<String,List<Option>> options(UUID tenant,Map<String,String> bindings){
  Objects.requireNonNull(tenant);var result=new LinkedHashMap<String,List<Option>>();var cache=new HashMap<String,List<Option>>();
  // 历史项即使被旧版本误设为启用也仅用于解析，不能绕过统一标准成为新选项。
  for(var binding:bindings.entrySet())result.put(binding.getKey(),cache.computeIfAbsent(binding.getValue(),code->mapper.options(tenant.toString(),code).stream().map(row->new Option((String)row.get("value"),(String)row.get("name"),"ENABLED".equals(row.get("dictionaryStatus"))&&"ENABLED".equals(row.get("itemStatus"))&&DictionaryValueStandard.validValue((String)row.get("value"))&&DictionaryValueStandard.validCode((String)row.get("code")))).toList()));
  return result;
 }
 @Override public boolean reference(UUID tenant,UUID actor,String consumer,String reference,Map<String,String> bindings,Map<String,String> values,Map<String,String> previous){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("DICTIONARY_REFERENCE_REQUIRES_TRANSACTION");
  // 按字典编码排序加父锁，与字典维护共用锁，避免校验与并发停用/删除之间产生竞态。
  var locked=new HashMap<String,Map<String,Object>>();
  for(String code:new TreeSet<>(bindings.values())){var rows=mapper.lockDictionary(tenant.toString(),code);if(!rows.isEmpty())locked.put(code,rows.getFirst());}
  for(var binding:bindings.entrySet()){
   String value=values.getOrDefault(binding.getKey(),"").trim();if(value.isEmpty())continue;
   boolean unchanged=value.equals(previous.getOrDefault(binding.getKey(),""));var dictionary=locked.get(binding.getValue());
   if(dictionary==null){if(unchanged)continue;return false;}
   var items=mapper.items(tenant.toString(),(String)dictionary.get("id"),value);
   if(items.isEmpty()){if(unchanged)continue;return false;}
   if(!unchanged&&(!DictionaryValueStandard.validValue(value)||!DictionaryValueStandard.validCode((String)items.getFirst().get("code"))||!"ENABLED".equals(dictionary.get("status"))||!"ENABLED".equals(items.getFirst().get("status"))))return false;
   // 引用保留历史，字段后续改值也不删除旧引用；配置删除不能破坏历史业务含义。
   mapper.reference(tenant.toString(),(String)items.getFirst().get("id"),consumer,reference,actor==null?null:actor.toString());
  }
  return true;
 }
}
