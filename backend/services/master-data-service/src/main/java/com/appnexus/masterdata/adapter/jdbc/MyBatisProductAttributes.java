package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.masterdata.api.product.ProductMainData.Value;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;

import java.util.*;
/** 属性固定存储重写适配；仅在新建或持有父锁的聚合事务内删除重写，创建身份和时间不可伪造或覆盖。 */
public final class MyBatisProductAttributes {
 private final ProductAttributeValueMapper mapper;
 /** @param mapper 与父商品、版本及历史事件共用事务的数据源，禁止独立提交 */
 public MyBatisProductAttributes(ProductAttributeValueMapper mapper){this.mapper=mapper;}
 /** @param values 原值按模板项索引 @param choices 原选项或自定义文本索引，保留创建审计 */
 public record ValueHistory(Map<String,Map<String,Object>> values,Map<String,Map<String,Object>> choices){}
 /** @param c 可信主体 @param id 当前租户商品UUID @return 原值及选项创建审计，历史NULL原样保留 */
 public ValueHistory history(VerifiedTenantContext c,String id){
  var originals=new HashMap<String,Map<String,Object>>();
  for(var old:mapper.statement1(tenant(c),id))originals.put((String)old.get("item"),old);
  var oldChoices=new HashMap<String,Map<String,Object>>();
  for(var old:mapper.statement2(tenant(c),id))oldChoices.put(old.get("item")+":"+(old.get("optionId")==null?"custom:"+old.get("customText"):old.get("optionId")),old);

  return new ValueHistory(originals,oldChoices);
 }
 /** @param c 可信主体 @param id 已锁商品UUID @param config 固定配置UUID @param preparedValues 已完成应用校验的值 @param preserved 迁移前保存的创建审计，可空时从当前库读取；必须同事务 */
 public void replace(VerifiedTenantContext c,String id,String config,List<com.appnexus.masterdata.application.product.ProductAttributePreparation.PreparedValue> preparedValues,ValueHistory preserved){
  var history=preserved==null?history(c,id):preserved;
  var originals=history.values();var oldChoices=history.choices();
  // 删除重写值仅发生在持有父聚合锁/新建事务内，历史快照保留旧值，不允许半提交。
  mapper.statement3(tenant(c),id);
  mapper.statement4(tenant(c),id);
  for(var prepared:preparedValues){
   Value value=prepared.source();String revision=prepared.revision(),type=prepared.type();
   Object text=prepared.text(),integer=prepared.integer(),decimal=prepared.decimal(),bool=prepared.bool(),date=prepared.date();
   List<String> options=value.optionIds()==null?List.of():value.optionIds();List<String> customs=value.customValues()==null?List.of():value.customValues();
   var original=originals.get(value.templateItemId());String valueId=original==null?UUID.randomUUID().toString():(String)original.get("id");
   mapper.statement5(valueId,tenant(c),id,config,value.templateItemId(),revision,type,text,integer,decimal,bool,date,original==null?actor(c):original.get("creator"),original==null?null:original.get("created_at"),actor(c));
   for(String option:options){uuid(option);if(mapper.statement6(tenant(c),revision,option)!=1)throw invalid();
    var oldChoice=oldChoices.get(value.templateItemId()+":"+option);
    mapper.statement7(oldChoice==null?UUID.randomUUID().toString():oldChoice.get("id"),tenant(c),valueId,revision,option,oldChoice==null?actor(c):oldChoice.get("creator"),oldChoice==null?null:oldChoice.get("created_at"),actor(c));
   }
   for(String custom:customs){var oldCustom=oldChoices.get(value.templateItemId()+":custom:"+custom);mapper.statement8(oldCustom==null?UUID.randomUUID().toString():oldCustom.get("id"),tenant(c),valueId,revision,custom,oldCustom==null?actor(c):oldCustom.get("creator"),oldCustom==null?null:oldCustom.get("created_at"),actor(c));}
  }
 }
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException error){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
