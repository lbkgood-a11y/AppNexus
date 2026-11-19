package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.masterdata.api.product.ProductMainData.Value;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** 属性固定存储重写适配；仅在新建或持有父锁的聚合事务内删除重写，创建身份和时间不可伪造或覆盖。 */
public final class JdbcProductAttributes {
 private final JdbcTemplate jdbc;
 /** @param jdbc 与父商品、版本及历史事件共用事务的数据源，禁止独立提交 */
 public JdbcProductAttributes(JdbcTemplate jdbc){this.jdbc=jdbc;}
 /** @param values 原值按模板项索引 @param choices 原选项或自定义文本索引，保留创建审计 */
 public record ValueHistory(Map<String,Map<String,Object>> values,Map<String,Map<String,Object>> choices){}
 /** @param c 可信主体 @param id 当前租户商品UUID @return 原值及选项创建审计，历史NULL原样保留 */
 public ValueHistory history(VerifiedTenantContext c,String id){
  var originals=new HashMap<String,Map<String,Object>>();
  for(var old:jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(template_item_id) item,BIN_TO_UUID(created_by) creator,created_at FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",tenant(c),id))originals.put((String)old.get("item"),old);
  var oldChoices=new HashMap<String,Map<String,Object>>();
  for(var old:jdbc.queryForList("SELECT BIN_TO_UUID(ch.id) id,BIN_TO_UUID(v.template_item_id) item,BIN_TO_UUID(ch.option_id) optionId,ch.custom_text customText,BIN_TO_UUID(ch.created_by) creator,ch.created_at FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?)",tenant(c),id))oldChoices.put(old.get("item")+":"+(old.get("optionId")==null?"custom:"+old.get("customText"):old.get("optionId")),old);

  return new ValueHistory(originals,oldChoices);
 }
 /** @param c 可信主体 @param id 已锁商品UUID @param config 固定配置UUID @param preparedValues 已完成应用校验的值 @param preserved 迁移前保存的创建审计，可空时从当前库读取；必须同事务 */
 public void replace(VerifiedTenantContext c,String id,String config,List<com.appnexus.masterdata.application.product.ProductAttributePreparation.PreparedValue> preparedValues,ValueHistory preserved){
  var history=preserved==null?history(c,id):preserved;
  var originals=history.values();var oldChoices=history.choices();
  // 删除重写值仅发生在持有父聚合锁/新建事务内，历史快照保留旧值，不允许半提交。
  jdbc.update("DELETE ch FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?)",tenant(c),id);
  jdbc.update("DELETE FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",tenant(c),id);
  for(var prepared:preparedValues){
   Value value=prepared.source();String revision=prepared.revision(),type=prepared.type();
   Object text=prepared.text(),integer=prepared.integer(),decimal=prepared.decimal(),bool=prepared.bool(),date=prepared.date();
   List<String> options=value.optionIds()==null?List.of():value.optionIds();List<String> customs=value.customValues()==null?List.of():value.customValues();
   var original=originals.get(value.templateItemId());String valueId=original==null?UUID.randomUUID().toString():(String)original.get("id");
   jdbc.update("INSERT INTO md_product_v1_attribute_value(id,tenant_id,product_id,category_config_id,template_item_id,attribute_revision_id,value_type,text_value,integer_value,decimal_value,boolean_value,date_value,created_by,created_at,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,?,?,UUID_TO_BIN(?),COALESCE(?,CURRENT_TIMESTAMP(6)),UUID_TO_BIN(?))",valueId,tenant(c),id,config,value.templateItemId(),revision,type,text,integer,decimal,bool,date,original==null?actor(c):original.get("creator"),original==null?null:original.get("created_at"),actor(c));
   for(String option:options){uuid(option);if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",Integer.class,tenant(c),revision,option)!=1)throw invalid();
    var oldChoice=oldChoices.get(value.templateItemId()+":"+option);
    jdbc.update("INSERT INTO md_product_v1_attribute_choice(id,tenant_id,value_id,attribute_revision_id,option_id,created_by,created_at,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),COALESCE(?,CURRENT_TIMESTAMP(6)),UUID_TO_BIN(?))",oldChoice==null?UUID.randomUUID().toString():oldChoice.get("id"),tenant(c),valueId,revision,option,oldChoice==null?actor(c):oldChoice.get("creator"),oldChoice==null?null:oldChoice.get("created_at"),actor(c));
   }
   for(String custom:customs){var oldCustom=oldChoices.get(value.templateItemId()+":custom:"+custom);jdbc.update("INSERT INTO md_product_v1_attribute_choice(id,tenant_id,value_id,attribute_revision_id,custom_text,created_by,created_at,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),COALESCE(?,CURRENT_TIMESTAMP(6)),UUID_TO_BIN(?))",oldCustom==null?UUID.randomUUID().toString():oldCustom.get("id"),tenant(c),valueId,revision,custom,oldCustom==null?actor(c):oldCustom.get("creator"),oldCustom==null?null:oldCustom.get("created_at"),actor(c));}
  }
 }
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException error){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
