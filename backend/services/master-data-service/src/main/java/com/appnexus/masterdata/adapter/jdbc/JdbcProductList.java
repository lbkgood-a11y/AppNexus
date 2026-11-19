package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductListFilter.AttributeFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
/** 商品列表固定SQL与JSON过滤适配；仅允许固定列、运算符和单配置最多三项条件，所有值参数绑定。 */
public final class JdbcProductList implements com.appnexus.masterdata.application.product.ProductListReads {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 /** @param jdbc 可信租户查询数据源 @param json 原过滤JSON协议解析器 */
 public JdbcProductList(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 /** {@inheritDoc} */
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson){
if(name==null||name.length()>200||code==null||code.length()>64||page<1||page>1000000||size<1||size>100)throw invalid();
  if(categoryId!=null)uuid(categoryId);
  if(form!=null&&!Set.of("PHYSICAL","SERVICE","COMPOSITE").contains(form)||status!=null&&!Set.of("DRAFT","ENABLED","DISABLED").contains(status))throw invalid();
  var args=new ArrayList<Object>();args.add(tenant(c));args.add(name.trim());args.add(code.trim());
  var where=new StringBuilder(" WHERE tenant_id=UUID_TO_BIN(?) AND LOCATE(?,name)>0 AND LOCATE(?,code)>0 AND draft_archived_at IS "+(archived?"NOT NULL":"NULL"));
  if(categoryId!=null){where.append(" AND category_id=UUID_TO_BIN(?)");args.add(categoryId);}
  if(form!=null){where.append(" AND product_form=?");args.add(form);}
  if(status!=null){where.append(" AND status=?");args.add(status);}
  if(configId!=null){uuid(configId);where.append(" AND category_config_id=UUID_TO_BIN(?)");args.add(configId);}
  applyAttributeFilters(c,configId,filtersJson,where,args);
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1"+where,Long.class,args.toArray());
  args.add(size);args.add((page-1)*size);
  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,code,name,product_form productForm,status,structure_state structureState,draft_archived_at archivedAt,version,updated_at updatedAt FROM md_product_v1"+where+" ORDER BY code,id LIMIT ? OFFSET ?",args.toArray());
  return Map.of("rows",rows,"total",total);
 }

 private void applyAttributeFilters(VerifiedTenantContext c,String config,String input,StringBuilder where,List<Object> args){
  if(input==null||input.isBlank())return;if(input.length()>5000||config==null)throw invalid();
  List<AttributeFilter> filters;
  try{filters=json.readValue(input,json.getTypeFactory().constructCollectionType(List.class,AttributeFilter.class));}catch(Exception e){throw invalid();}
  if(filters==null||filters.size()>3)throw invalid();
  for(var filter:filters){
   if(filter==null||filter.operator()==null||filter.values()==null||filter.values().isEmpty()||filter.values().size()>50||filter.values().stream().anyMatch(Objects::isNull))throw invalid();uuid(filter.templateItemId());
   var definitions=jdbc.queryForList("SELECT r.data_type,r.numeric_scale,BIN_TO_UUID(r.id) revisionId FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) AND ci.template_item_id=UUID_TO_BIN(?) AND i.searchable=TRUE",tenant(c),config,filter.templateItemId());
   if(definitions.size()!=1)throw invalid();var d=definitions.getFirst();String type=(String)d.get("data_type"),column;var values=new ArrayList<Object>();
   if(Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)){
    if(!Set.of("eq","anyOf").contains(filter.operator())||filter.operator().equals("eq")&&filter.values().size()!=1)throw invalid();
    for(String option:filter.values()){uuid(option);if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Integer.class,tenant(c),d.get("revisionId"),option)!=1)throw invalid();}
    where.append(" AND EXISTS(SELECT 1 FROM md_product_v1_attribute_value av JOIN md_product_v1_attribute_choice ch ON ch.tenant_id=av.tenant_id AND ch.value_id=av.id WHERE av.tenant_id=md_product_v1.tenant_id AND av.product_id=md_product_v1.id AND av.template_item_id=UUID_TO_BIN(?) AND ch.option_id IN (").append(String.join(",",Collections.nCopies(filter.values().size(),"UUID_TO_BIN(?)"))).append("))");
    args.add(filter.templateItemId());args.addAll(filter.values());continue;
   }
   column=switch(type){case "INTEGER"->"integer_value";case "DECIMAL"->"decimal_value";case "DATE"->"date_value";case "BOOLEAN"->"boolean_value";default->throw invalid();};
   values.addAll(com.appnexus.masterdata.domain.product.ProductFilterRules.scalar(type,type.equals("DECIMAL")?((Number)d.get("numeric_scale")).intValue():0,filter.operator(),filter.values()));
   String predicate=switch(filter.operator()){case "eq"->"=?";case "gte"->">=?";case "lte"->"<=?";case "between"->" BETWEEN ? AND ?";default->throw invalid();};
   where.append(" AND EXISTS(SELECT 1 FROM md_product_v1_attribute_value av WHERE av.tenant_id=md_product_v1.tenant_id AND av.product_id=md_product_v1.id AND av.template_item_id=UUID_TO_BIN(?) AND av.").append(column).append(predicate).append(")");
   args.add(filter.templateItemId());args.addAll(values);
  }
 }

 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
