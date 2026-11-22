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
 private final com.appnexus.platform.authorization.ScopedWriteGate gate;
 private final org.springframework.transaction.support.TransactionTemplate transactions;
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 /** @param mapper 固定字典查询 @param gate 事务内绑定原业务响应引用 @param manager 宿主事务管理器 */
 public MyBatisBusinessValueCatalog(BusinessValueMapper mapper,com.appnexus.platform.authorization.ScopedWriteGate gate,
     org.springframework.transaction.PlatformTransactionManager manager){
   this.mapper=Objects.requireNonNull(mapper);
   this.gate=gate;
   this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager);
 }

 /** {@inheritDoc} */
 @Override public Map<NameKey,String> names(UUID tenant,List<NameKey> keys){
   Objects.requireNonNull(tenant);
   if(keys.isEmpty())return Map.of();
   if(keys.size()>200)throw new IllegalArgumentException("DICTIONARY_NAME_BATCH_TOO_LARGE");
   String batch=json.writeValueAsString(keys.stream().distinct().toList());
   return transactions.execute(status->{
     gate.authorizeServiceOperation(tenant,"dictionary-reference:resolve-names",List.of(batch),()->true);
     var result=new HashMap<NameKey,String>();
     for(var row:mapper.names(tenant.toString(),batch)){
       result.put(new NameKey((String)row.get("dictionary"),(String)row.get("value")),(String)row.get("name"));
     }
     return Map.copyOf(result);
   });
 }
 /** {@inheritDoc} */
 @Override public Map<String,List<Option>> options(UUID tenant,Map<String,String> bindings){
  Objects.requireNonNull(tenant);
  if(bindings.isEmpty())return Map.of();
  var codes=bindings.values().stream().distinct().toList();
  var options=new HashMap<String,List<Option>>();
  for(int offset=0;offset<codes.size();offset+=200){
   String batch=json.writeValueAsString(codes.subList(offset,Math.min(offset+200,codes.size())));
   var loaded=transactions.execute(status->{
     gate.authorizeServiceOperation(tenant,"dictionary-reference:read-options",List.of(batch),()->true);
     return mapper.options(tenant.toString(),batch);
   });
   // 历史项即使被旧版本误设为启用也仅用于解析，不能绕过统一标准成为新选项。
   for(var row:loaded){
    String value=(String)row.get("value");
    boolean enabled="ENABLED".equals(row.get("dictionaryStatus"))&&"ENABLED".equals(row.get("itemStatus"))
        &&DictionaryValueStandard.validValue(value)&&DictionaryValueStandard.validCode((String)row.get("code"));
    options.computeIfAbsent((String)row.get("dictionary"),ignored->new ArrayList<>())
        .add(new Option(value,(String)row.get("name"),enabled));
   }
  }
  var result=new LinkedHashMap<String,List<Option>>();
  bindings.forEach((field,code)->result.put(field,List.copyOf(options.getOrDefault(code,List.of()))));
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
