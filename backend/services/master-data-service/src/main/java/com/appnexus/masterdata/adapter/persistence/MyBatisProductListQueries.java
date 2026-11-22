package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.adapter.persistence.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.api.masterdata.MasterData;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductListFilter.AttributeFilter;
/** 商品列表固定SQL与JSON过滤适配；仅允许固定列、运算符和单配置最多三项条件，所有值参数绑定。 */
public final class MyBatisProductListQueries implements com.appnexus.masterdata.application.product.ProductListQueries {
 private final ProductAccess access;private final ProductListMapper mapper;private final ObjectMapper json;
 /** @param mapper 可信租户固定列表映射 @param json 原过滤JSON协议解析器 */
 public MyBatisProductListQueries(ProductListMapper mapper,ObjectMapper json,ProductAccess access){this.access=access;this.mapper=mapper;this.json=json;}
 /** {@inheritDoc} */
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson,boolean includeDescendants,String keyword){
if(keyword==null||keyword.length()>200||name==null||name.length()>200||code==null||code.length()>64||page<1||page>1000000||size<1||size>100)throw invalid();
  if(categoryId!=null)uuid(categoryId);
  if(form!=null&&!Set.of("PHYSICAL","SERVICE","COMPOSITE").contains(form)||status!=null&&!Set.of("DRAFT","ENABLED","DISABLED").contains(status))throw invalid();
  if(configId!=null)uuid(configId);
  var query=new ProductListMapper.Query(access.predicate(c,"read"),name.trim(),code.trim(),keyword.trim(),categoryId,includeDescendants,form,status,archived,configId,attributeFilters(c,configId,filtersJson),size,(page-1)*size);
  return Map.of("rows",mapper.list(tenantBytes(c),query),"total",mapper.count(tenantBytes(c),query));
 }

 private List<ProductListMapper.Filter> attributeFilters(VerifiedTenantContext c,String config,String input){
  var result=new ArrayList<ProductListMapper.Filter>();
  if(input==null||input.isBlank())return List.of();if(input.length()>5000||config==null)throw invalid();
  List<AttributeFilter> filters;
  try{filters=json.readValue(input,json.getTypeFactory().constructCollectionType(List.class,AttributeFilter.class));}catch(Exception e){throw invalid();}
  if(filters==null||filters.size()>3)throw invalid();
  for(var filter:filters){
   if(filter==null||filter.operator()==null||filter.values()==null||filter.values().isEmpty()||filter.values().size()>50||filter.values().stream().anyMatch(Objects::isNull))throw invalid();uuid(filter.templateItemId());
   var definitions=mapper.definitions(tenantBytes(c),config,filter.templateItemId());
   if(definitions.size()!=1)throw invalid();var d=definitions.getFirst();String type=(String)d.get("data_type");var values=new ArrayList<Object>();
   if(Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)){
    if(!Set.of("eq","anyOf").contains(filter.operator())||filter.operator().equals("eq")&&filter.values().size()!=1)throw invalid();
    for(String option:filter.values()){uuid(option);if(mapper.optionCount(tenantBytes(c),d.get("revisionId"),option)!=1)throw invalid();}
    result.add(new ProductListMapper.Filter(filter.templateItemId(),type,filter.operator(),new ArrayList<Object>(filter.values())));continue;
   }
   values.addAll(com.appnexus.masterdata.domain.product.ProductFilterRules.scalar(type,type.equals("DECIMAL")?((Number)d.get("numeric_scale")).intValue():0,filter.operator(),filter.values()));
   result.add(new ProductListMapper.Filter(filter.templateItemId(),type,filter.operator(),values));
  }
  return List.copyOf(result);
 }

 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
