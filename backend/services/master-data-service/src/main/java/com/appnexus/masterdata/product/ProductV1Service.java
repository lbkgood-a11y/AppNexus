package com.appnexus.masterdata.product;

import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import com.appnexus.contracts.operation.OperationPreviewProof;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 商品主档及类型化主档属性聚合；草稿仅校验已填，启用才校验完整性。
 * 所有写入先授权，商品CAS、动态值、四审计和追加历史原子提交；旧商品入口由强制对账切换迁移保护，原表仅用于历史核对。
 */
@org.springframework.stereotype.Service
public final class ProductV1Service {
 private final JdbcTemplate jdbc;
 private final AccessAuthorization access;
 private final TransactionTemplate tx;
 private final TransactionTemplate migrationTx;
 private final OperationPreviewProof previewProof;
 private final ObjectMapper json=new ObjectMapper();

 /**
  * 草稿创建；不接收状态、租户、审计或客户端版本。
  * @param code 租户内唯一编码，1至64个ASCII字符
  * @param name 名称，1至200字符
  * @param categoryId 分类可空；configId非空时必须匹配
  * @param configId 固定发布配置UUID，不接受latest
  * @param productForm PHYSICAL/SERVICE，可空；启用必填
  * @param unitId 实物启用必填，服务按配置规则
  * @param values 类型化属性值列表，可空即无值；已绑定配置下required可缺
  * @param barcode 条码文本，最多500字符，缺省空；保留前导零，不推导SKU
  */
 public record Input(String code,String name,String shortName,String categoryId,String configId,String productForm,String unitId,String description,String brand,String origin,List<Value> values,String barcode){
  /** 内部原调用不提供条码时默认空；条码始终文本，不丢前导零。 */
  public Input(String code,String name,String shortName,String categoryId,String configId,String productForm,String unitId,String description,String brand,String origin,List<Value> values){this(code,name,shortName,categoryId,configId,productForm,unitId,description,brand,origin,values,"");}
 }
 /**
  * 动态属性输入；标量与选项互斥，不允许客户端指定类型/修订或单位。
  * @param templateItemId 当前配置真实模板项UUID
  * @param value 标量文本，整数/小数为精确十进制，日期为ISO自然日，布尔为true/false
  * @param optionIds 枚举选项UUID列表；单选总数恰1，多选总数1至50，不接受另一修订项
  * @param customValues 模板allowCustom=true时可填的明确自定义文本，不新增属性选项；与标准选项共同参与数量校验
  */
 public record Value(String templateItemId,String value,List<String> optionIds,List<String> customValues){
  /** 不传自定义值时维持原输入契约；普通标准枚举不受影响。 */
  public Value(String templateItemId,String value,List<String> optionIds){this(templateItemId,value,optionIds,List.of());}
 }
 /** 普通修改只接收主档资料/当前定义的属性值；NULL values保留，空列表明确清空。 */
 public record Update(String name,String shortName,String description,String brand,String origin,List<Value> values,Long version,String barcode){
  /** 原内部调用不修改条码，NULL保持原值。 */
  public Update(String name,String shortName,String description,String brand,String origin,List<Value> values,long version){this(name,shortName,description,brand,origin,values,version,null);}
 }
 /** 显式启停；目标仅ENABLED/DISABLED，携带商品当前非负版本。 */
 public record Status(String status,Long version){
  /** 内部调用可传原始long，HTTP仍按可空规范字段拒绝缺省。 */
  public Status(String status,long version){this(status,Long.valueOf(version));}
 }
 /** 草稿归档恢复请求，版本必填；归档非删除，不能回收编码。 */
 public record Version(Long version){
  /** 内部调用使用非负long，HTTP缺省仍可识别。 */
  public Version(long version){this(Long.valueOf(version));}
 }

 /** @param jdbc 宿主UTC数据源 @param access 公开授权端口 @param manager 宿主事务管理器 */
 public ProductV1Service(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager,OperationPreviewProof previewProof){this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.migrationTx=new TransactionTemplate(manager);this.migrationTx.setTimeout(10);this.previewProof=previewProof;}

 /**
  * 商品分页；编码/名称分别筛选，未归档默认可见，真实total不以固定100条代替。
  * @param name 名称字面包含，最多200字符，默认空
  * @param code 编码字面包含，最多64字符，默认空
  * @param categoryId 可选分类UUID，只缩小原租户范围
  * @param form 可选PHYSICAL/SERVICE
  * @param status 可选DRAFT/ENABLED/DISABLED
  * @param archived 默认false只看未归档，true只看归档
  * @param page 页码1起，默认1，最大1000000
  * @param size 每页1至100，默认20
  */
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size){return list(c,name,code,categoryId,form,status,archived,page,size,null,null);}
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson){
  require(c,"read");if(name==null||name.length()>200||code==null||code.length()>64||page<1||page>1000000||size<1||size>100)throw invalid();
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

 /**
  * 动态筛选严格绑定一个确定配置，最多3条件且AND连接，不支持TEXT/历史/JSONPath。
  * @param templateItemId 当前配置标记searchable的模板项
  * @param operator eq/gte/lte/between/anyOf，按类型限定
  * @param values 标量十进制/日期/布尔字符串，枚举为固定选项UUID
  */
 public record AttributeFilter(String templateItemId,String operator,List<String> values){}
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
   if(!Set.of("eq","gte","lte","between").contains(filter.operator())||filter.operator().equals("between")&&filter.values().size()!=2||!filter.operator().equals("between")&&filter.values().size()!=1)throw invalid();
   try{
    switch(type){
     case "INTEGER"->{column="integer_value";for(String value:filter.values()){if(!value.matches("-?(0|[1-9][0-9]*)"))throw invalid();values.add(Long.valueOf(value));}}
     case "DECIMAL"->{column="decimal_value";for(String value:filter.values()){if(!value.matches("-?[0-9]+(\\.[0-9]+)?"))throw invalid();var number=new BigDecimal(value);if(number.scale()>((Number)d.get("numeric_scale")).intValue()||number.precision()-number.scale()>18)throw invalid();values.add(number);}}
     case "DATE"->{column="date_value";if(!Set.of("eq","between").contains(filter.operator()))throw invalid();for(String value:filter.values())values.add(LocalDate.parse(value));}
     case "BOOLEAN"->{column="boolean_value";if(!filter.operator().equals("eq")||!Set.of("true","false").contains(filter.values().getFirst()))throw invalid();values.add(Boolean.valueOf(filter.values().getFirst()));}
     default->throw invalid();
    }
   }catch(IllegalArgumentException|java.time.DateTimeException e){throw invalid();}
   if(filter.operator().equals("between")&&((Comparable)values.getFirst()).compareTo(values.get(1))>0)throw invalid();
   String predicate=switch(filter.operator()){case "eq"->"=?";case "gte"->">=?";case "lte"->"<=?";case "between"->" BETWEEN ? AND ?";default->throw invalid();};
   where.append(" AND EXISTS(SELECT 1 FROM md_product_v1_attribute_value av WHERE av.tenant_id=md_product_v1.tenant_id AND av.product_id=md_product_v1.id AND av.template_item_id=UUID_TO_BIN(?) AND av.").append(column).append(predicate).append(")");
   args.add(filter.templateItemId());args.addAll(values);
  }
 }

 /** 保留内部调用入口的默认分页；HTTP参数由固定Controller声明。 */
 public Object list(VerifiedTenantContext c){return list(c,"","",null,null,null,false,1,20);}

 /**
  * 商品原读取权限下的分类/单位候选；不要求建档用户获得配置维护权限。
  * @param kind 仅category/unit，不能选择任意表
  * @param keyword 字面编码/名称搜索，最多200字符
  * @param page 页码1起 @param size 每页1至100
  */
 public Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size){
  require(c,"read");if(keyword==null||keyword.length()>200||page<1||page>1000000||size<1||size>100)throw invalid();
  String table=switch(kind){case "category"->"md_product_category";case "unit"->"md_product_unit";default->throw invalid();};
  String where=" WHERE tenant_id=UUID_TO_BIN(?) AND enabled=TRUE AND (LOCATE(?,code)>0 OR LOCATE(?,name)>0)";
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+where,Long.class,tenant(c),keyword,keyword);
  String extra=kind.equals("category")?",BIN_TO_UUID(current_config_id) configId":",dimension_code dimensionCode,precision_scale precisionScale";
  return Map.of("rows",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,code,name"+extra+" FROM "+table+where+" ORDER BY code,id LIMIT ? OFFSET ?",tenant(c),keyword,keyword,size,(page-1)*size),"total",total);
 }

 /** 按分类读取固定配置版本目录；用于显式选择版本，不自动将旧商品升级。 */
 public Object configurations(VerifiedTenantContext c,String categoryId,int page,int size){
  require(c,"read");uuid(categoryId);if(page<1||page>1000000||size<1||size>100)throw invalid();
  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,revision_no revision,display_path_snapshot path FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?) ORDER BY revision_no DESC LIMIT ? OFFSET ?",tenant(c),categoryId,size,(page-1)*size);
  return Map.of("rows",rows,"total",jdbc.queryForObject("SELECT COUNT(*) FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?)",Long.class,tenant(c),categoryId));
 }
 /** 确定配置版本的动态表单元数据；历史退役定义可读，不追latest，也不扩大到字典写入权。 */
 public Map<String,Object> configuration(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  var configs=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(category_id) categoryId,revision_no revision,display_path_snapshot path,service_unit_required serviceUnitRequired FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),id);
  if(configs.isEmpty())throw changed();var config=new LinkedHashMap<String,Object>(configs.getFirst());
  var fields=jdbc.queryForList("SELECT BIN_TO_UUID(i.id) templateItemId,BIN_TO_UUID(i.attribute_id) attributeId,BIN_TO_UUID(i.attribute_revision_id) revisionId,ci.purpose,r.name,r.data_type dataType,r.numeric_scale numericScale,CAST(r.validation_json AS CHAR) validationJson,i.group_code groupCode,i.group_name groupName,i.required_flag requiredFlag,i.searchable,i.allow_custom allowCustom,BIN_TO_UUID(r.unit_id) unitId,u.code unitCode,u.name unitName FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) ORDER BY ci.purpose,i.sort_order,i.id",tenant(c),id);
  for(var field:fields)field.put("options",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,option_code code,label,enabled FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?) ORDER BY sort_order,id",tenant(c),field.get("revisionId")));
  config.put("fields",fields);var slots=new LinkedHashMap<String,Object>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  jdbc.query("SELECT purpose,BIN_TO_UUID(template_version_id) versionId FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(?) AND config_id=UUID_TO_BIN(?)",rs->{slots.put(rs.getString("purpose"),rs.getString("versionId"));},tenant(c),id);
  config.put("templates",slots);return config;
 }
 /** 授权详情，包括固定配置、动态值、completionIssues与可信审计，不能以列表快照替代。 */
 public Map<String,Object> read(VerifiedTenantContext c,String id){require(c,"read");uuid(id);return snapshot(c,id);}
 /** 创建仅DRAFT；已填始终合法，缺必填不阻止草稿；返回数据库真实版本和完整性。 */
 public Map<String,Object> create(VerifiedTenantContext c,Input i){
  require(c,"create");validate(i);String id=UUID.randomUUID().toString();
  return write(()->{
   validateReferences(c,i.categoryId(),i.configId(),i.productForm(),i.unitId(),true);
   jdbc.update("INSERT INTO md_product_v1(id,tenant_id,code,name,short_name,category_id,category_config_id,product_form,base_unit_id,description,brand,origin_text,barcode,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",
    id,tenant(c),i.code().trim(),i.name().trim(),i.shortName(),i.categoryId(),i.configId(),i.productForm(),i.unitId(),i.description(),i.brand(),i.origin(),Objects.toString(i.barcode(),""),actor(c),actor(c));
   saveValues(c,id,i.configId(),i.values());refreshCompleteness(c,id);event(c,id,"CREATE",null);return snapshot(c,id);
  });
 }
 /** 普通修改不改变结构；启用态仍完整才允许保存，失败保留原审计与全部值。 */
 public Map<String,Object> update(VerifiedTenantContext c,String id,Update input){
  require(c,"update");uuid(id);if(input==null||input.version()==null)throw invalid();text(input.name(),200,true);text(input.shortName(),100,false);text(input.brand(),200,false);text(input.origin(),200,false);text(input.description(),20000,false);text(input.barcode(),500,false);
  return write(()->{
   var before=locked(c,id,input.version());ensureNotInApproval(c,id);mutable(before);
   jdbc.update("UPDATE md_product_v1 SET name=?,short_name=?,description=?,brand=?,origin_text=?,barcode=COALESCE(?,barcode) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",input.name().trim(),input.shortName(),input.description(),input.brand(),input.origin(),input.barcode(),tenant(c),id);
   if(input.values()!=null)saveValues(c,id,(String)before.get("configId"),input.values());
   var issues=refreshCompleteness(c,id);if("ENABLED".equals(before.get("status"))&&!issues.isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
   bump(c,id,input.version());event(c,id,"UPDATE",before);return snapshot(c,id);
  });
 }
 /** 三态合法转换；DRAFT不能直接停用，普通写不能通过启停接口修改结构。 */
 public Map<String,Object> updateStatus(VerifiedTenantContext c,String id,Status input){
  require(c,"set_status");uuid(id);if(input==null||input.version()==null||input.status()==null||!Set.of("ENABLED","DISABLED").contains(input.status()))throw invalid();
  return write(()->{
   var before=locked(c,id,input.version());ensureNotInApproval(c,id);
   if(before.get("archivedAt")!=null||Objects.equals(before.get("status"),input.status())||"DRAFT".equals(before.get("status"))&&"DISABLED".equals(input.status()))throw changed();
   if("ENABLED".equals(input.status())){
    var reviews=jdbc.queryForList("SELECT state,product_version FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY created_at DESC LIMIT 1",tenant(c),id);if(!reviews.isEmpty()&&(!"APPROVED".equals(reviews.getFirst().get("state"))||((Number)reviews.getFirst().get("product_version")).longValue()!=input.version()))throw problem("PRODUCT_APPROVAL_REQUIRED");
    if("LEGACY_PENDING".equals(before.get("structureState")))throw new MasterData.Rejection(409,"PRODUCT_LEGACY_PENDING");
    validateReferences(c,(String)before.get("categoryId"),(String)before.get("configId"),(String)before.get("productForm"),(String)before.get("unitId"),false);
    if(!refreshCompleteness(c,id).isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
   }
   jdbc.update("UPDATE md_product_v1 SET status=? WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",input.status(),tenant(c),id);
   bump(c,id,input.version());event(c,id,"STATUS",before);return snapshot(c,id);
  });
 }
 /** 人工归档/恢复仅DRAFT，保留编码和历史，不运行自动清理。 */
 public Map<String,Object> archive(VerifiedTenantContext c,String id,Version input,boolean archived){
  require(c,"manage_draft");uuid(id);if(input==null||input.version()==null)throw invalid();
  return write(()->{
   var before=locked(c,id,input.version());ensureNotInApproval(c,id);if(!"DRAFT".equals(before.get("status"))||(before.get("archivedAt")!=null)==archived)throw changed();
   jdbc.update("UPDATE md_product_v1 SET draft_archived_at="+(archived?"CURRENT_TIMESTAMP(6)":"NULL")+" WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),id);
   bump(c,id,input.version());event(c,id,archived?"ARCHIVE":"RESTORE",before);return snapshot(c,id);
  });
 }
 /** 原读取权限内的追加历史；只读、分页，不提供任意表审计入口。 */
 public Object changes(VerifiedTenantContext c,String id,int page,int size){
  require(c,"read");uuid(id);if(page<1||page>1000000||size<1||size>100)throw invalid();row(c,id,false);
  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,to_version version,event_type eventType,CAST(before_snapshot AS CHAR) beforeSnapshot,CAST(after_snapshot AS CHAR) afterSnapshot,schema_version schemaVersion,created_at createdAt,BIN_TO_UUID(created_by) createdBy FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY to_version DESC LIMIT ? OFFSET ?",tenant(c),id,size,(page-1)*size);
  return Map.of("rows",rows,"total",jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Long.class,tenant(c),id));
 }


 /**
  * 全量目标结构，不接收latest、租户、操作者或客户端快照。
  * @param sourceVersion 当前父CAS，必填非负
  * @param sourceStructureRevision 当前结构修订，必填非负
  * @param sourceCategoryConfigId 当前固定配置UUID，NULL必须与源一致
  * @param categoryId 目标分类UUID，DRAFT允许空
  * @param configId 目标确定配置UUID，DRAFT允许空；执行一次切换整个四槽配置
  * @param productForm 目标PHYSICAL/SERVICE，DRAFT可空
  * @param unitId 目标单位，服务按目标配置可空
  * @param values 全量目标值，必须明确列表，空列表表示清空
  * @param droppedItemIds 源值中退出目标的模板项UUID，必须明确完整确认；排序不影响摘要
  * @param reason 结构更改理由，1至1000字符，不能填写凭据
  * @param legacyDisposition 旧档仅CONFIRMED_NONE才可转完整；原文永不修改
  */
 public record Migration(Long sourceVersion,Long sourceStructureRevision,String sourceCategoryConfigId,String categoryId,String configId,String productForm,String unitId,List<Value> values,List<String> droppedItemIds,String reason,String legacyDisposition,String specificationMode,List<String> retiredSpecificationIds){
  /** 原V1调用保持当前规格模式，不默认清除后续规格；新模式变更必须显式确认退出规格身份。 */
  public Migration(Long sourceVersion,Long sourceStructureRevision,String sourceCategoryConfigId,String categoryId,String configId,String productForm,String unitId,List<Value> values,List<String> droppedItemIds,String reason,String legacyDisposition){this(sourceVersion,sourceStructureRevision,sourceCategoryConfigId,categoryId,configId,productForm,unitId,values,droppedItemIds,reason,legacyDisposition,null,List.of());}
 }
 /** 执行原封不动的预览载荷；token仅内存使用，不记录日志/历史。 */
 public record MigrationCommand(Migration change,String previewToken){}

 /**
  * 只读预览不创建解析任务、临时值或幂等记录；签名绑定可信主体、源CAS、全量目标及准入摘要。
  * @return 源摘要、目标、完整性问题、退出项、签名及UTC到期点；不承诺执行必成功
  */
 public Map<String,Object> previewStructure(VerifiedTenantContext c,String id,Migration change){
  require(c,"migrate_structure");require(c,"read");uuid(id);validateMigration(change);
  var readOnly=new TransactionTemplate(tx.getTransactionManager());readOnly.setReadOnly(true);
  return readOnly.execute(status->{
   var before=snapshot(c,id);migrationSource(before,change);
   validateReferences(c,change.categoryId(),change.configId(),change.productForm(),change.unitId(),true,false);
   prepareValues(c,change.configId(),change.values());
   var issues=targetIssues(c,before,change);var dropped=droppedValues(before,change);
   if(!new TreeSet<>(change.droppedItemIds()).equals(new TreeSet<>(dropped)))throw problem("PRODUCT_DROPPED_VALUES_UNCONFIRMED");
   var proof=previewProof.issue(binding(c,id,change));
   var response=new LinkedHashMap<String,Object>();
   response.put("sourceVersion",before.get("version"));response.put("sourceStructureRevision",before.get("structureRevision"));
   response.put("sourceConfigId",before.get("configId"));response.put("sourceStatus",before.get("status"));
   response.put("target",change);response.put("completionIssues",issues);response.put("droppedItemIds",dropped);
   response.put("canonicalPayloadDigest",payloadDigest(change));response.put("previewToken",proof.token());response.put("expiresAt",proof.expiresAt().toString());
   response.put("executable",issues.isEmpty()||"DRAFT".equals(before.get("status")));
   return response;
  });
 }

 /**
  * 显式迁移：先重验权限，成功重放不再检查旧CAS/token，不再次写商品/历史。
  * 首次执行在父商品锁后重查幂等结果，CAS、目标引用、值、四审计及结果同事务提交。
  * @param key Idempotency-Key，规范UUID，作用域为可信租户/账号/商品/操作；重放窗口7天
  * @throws MasterData.Rejection 异载荷同键/过期预览/源版本变化为409；错误值与未确认退出为拒绝
  */
 public Map<String,Object> migrateStructure(VerifiedTenantContext c,String id,MigrationCommand command,String key){
  require(c,"migrate_structure");require(c,"read");uuid(id);uuid(key);
  if(command==null||command.change()==null)throw invalid();Migration change=command.change();validateMigration(change);
  row(c,id,false);String digest=payloadDigest(change);
  var existing=requestResult(c,id,key,digest,false);if(existing!=null)return existing;
  try{
   return migrationTx.execute(status->{
    // 当前商品写入均以父商品为串行点；目录写入从不等待商品锁。共享引用按分类→模板→单位锁定。
    row(c,id,true);ensureNotInApproval(c,id);
    var replay=requestResult(c,id,key,digest,true);if(replay!=null)return replay;
    var before=snapshot(c,id);migrationSource(before,change);
    validateReferences(c,change.categoryId(),change.configId(),change.productForm(),change.unitId(),true);
    prepareValues(c,change.configId(),change.values());
    if(!previewProof.verify(command.previewToken(),binding(c,id,change)))throw problem("PRODUCT_PREVIEW_CHANGED_OR_EXPIRED");
    if(!new TreeSet<>(change.droppedItemIds()).equals(new TreeSet<>(droppedValues(before,change))))throw problem("PRODUCT_DROPPED_VALUES_UNCONFIRMED");
    var issues=targetIssues(c,before,change);
    if(!"DRAFT".equals(before.get("status"))&&!issues.isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
    // MySQL没有延迟外键；先撤旧值再改父配置，再写目标值，中间态仅存在于同一事务。
    var originalAudit=valueHistory(c,id);clearValues(c,id);
    jdbc.update("UPDATE md_product_v1 SET category_id=UUID_TO_BIN(?),category_config_id=UUID_TO_BIN(?),product_form=?,base_unit_id=UUID_TO_BIN(?),structure_state='INCOMPLETE',structure_revision=structure_revision+1,legacy_specification_disposition=CASE WHEN legacy_enabled_snapshot IS NOT NULL THEN COALESCE(?,legacy_specification_disposition) ELSE legacy_specification_disposition END WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
     change.categoryId(),change.configId(),change.productForm(),change.unitId(),change.legacyDisposition(),tenant(c),id);
    if(change.retiredSpecificationIds()!=null)for(String spec:change.retiredSpecificationIds())jdbc.update("UPDATE md_product_specification SET retired_at=CURRENT_TIMESTAMP(6),enabled=FALSE,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND retired_at IS NULL",actor(c),tenant(c),id,spec);
    if(change.specificationMode()!=null)jdbc.update("UPDATE md_product_v1 SET specification_mode=? WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",change.specificationMode(),tenant(c),id);
    if(!"COMPOSITE".equals(change.productForm()))jdbc.update("UPDATE md_product_component SET enabled=FALSE,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND enabled=TRUE",actor(c),tenant(c),id);
    saveValues(c,id,change.configId(),change.values(),originalAudit);refreshCompleteness(c,id);bump(c,id,change.sourceVersion());
    String eventId=event(c,id,"STRUCTURE",before,change.reason());
    long version=change.sourceVersion()+1,revision=change.sourceStructureRevision()+1;
    var result=new LinkedHashMap<String,Object>();result.put("productId",id);result.put("resultVersion",version);result.put("resultStructureRevision",revision);result.put("changeId",eventId);
    jdbc.update("INSERT INTO md_product_structure_request(id,tenant_id,account_id,product_id,request_key,payload_digest,result_product_version,result_structure_revision,change_id,result_json,expires_at,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UNHEX(?),?,?,UUID_TO_BIN(?),?,TIMESTAMPADD(DAY,7,CURRENT_TIMESTAMP(6)),UUID_TO_BIN(?),UUID_TO_BIN(?))",
     UUID.randomUUID().toString(),tenant(c),actor(c),id,key,digest,version,revision,eventId,json.writeValueAsString(result),actor(c),actor(c));
    var completed=requestResult(c,id,key,digest,true);completed.put("replayed",false);return completed;
   });
  }catch(org.springframework.dao.PessimisticLockingFailureException|org.springframework.dao.QueryTimeoutException|org.springframework.transaction.TransactionTimedOutException e){
   throw problem("PRODUCT_MIGRATION_IN_PROGRESS");
  }catch(DataIntegrityViolationException e){throw problem("PRODUCT_MIGRATION_CONFLICT");}
 }

 private Map<String,Object> requestResult(VerifiedTenantContext c,String id,String key,String digest,boolean lock){
  var rows=jdbc.queryForList("SELECT HEX(payload_digest) digest,result_product_version version,result_structure_revision revision,BIN_TO_UUID(change_id) changeId,expires_at,expires_at>CURRENT_TIMESTAMP(6) valid FROM md_product_structure_request WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND operation_code='migrateStructure' AND request_key=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),tenant(c),actor(c),id,key);
  if(rows.isEmpty())return null;var record=rows.getFirst();
  if(!digest.equalsIgnoreCase((String)record.get("digest")))throw problem("PRODUCT_IDEMPOTENCY_KEY_REUSED");
  if(((Number)record.get("valid")).intValue()!=1)throw problem("PRODUCT_IDEMPOTENCY_WINDOW_EXPIRED");
  var result=new LinkedHashMap<String,Object>();result.put("productId",id);result.put("resultVersion",record.get("version"));result.put("resultStructureRevision",record.get("revision"));result.put("changeId",record.get("changeId"));result.put("replayUntil",((java.sql.Timestamp)record.get("expires_at")).toInstant().toString());result.put("replayed",true);return result;
 }
 private static void validateMigration(Migration change){
  if(change==null||change.sourceVersion()==null||change.sourceStructureRevision()==null||change.sourceVersion()<0||change.sourceStructureRevision()<0||change.values()==null||change.droppedItemIds()==null||change.values().size()>200||change.droppedItemIds().size()>200)throw invalid();
  if(change.specificationMode()!=null&&!Set.of("NONE","STANDARD","CUSTOM").contains(change.specificationMode()))throw invalid();if(change.retiredSpecificationIds()!=null){if(change.retiredSpecificationIds().size()>500)throw invalid();for(String x:change.retiredSpecificationIds())uuid(x);}
  text(change.reason(),1000,true);
  var itemIds=new HashSet<String>();for(var value:change.values()){if(value==null)throw invalid();uuid(value.templateItemId());if(!itemIds.add(value.templateItemId()))throw invalid();if(value.customValues()!=null&&(value.customValues().size()>50||value.customValues().stream().anyMatch(Objects::isNull)))throw invalid();if(value.optionIds()!=null){if(value.optionIds().size()>50||new HashSet<>(value.optionIds()).size()!=value.optionIds().size())throw invalid();for(String option:value.optionIds())uuid(option);}}
  if(change.sourceCategoryConfigId()!=null)uuid(change.sourceCategoryConfigId());
  for(String item:change.droppedItemIds())uuid(item);
  if(new HashSet<>(change.droppedItemIds()).size()!=change.droppedItemIds().size())throw invalid();
 }
 private void migrationSource(Map<String,Object> before,Migration change){
  if(Boolean.TRUE.equals(before.get("approvalPending")))throw problem("PRODUCT_APPROVAL_IN_PROGRESS");
  if(((Number)before.get("version")).longValue()!=change.sourceVersion()||((Number)before.get("structureRevision")).longValue()!=change.sourceStructureRevision()||!Objects.equals(before.get("configId"),change.sourceCategoryConfigId()))throw problem("PRODUCT_SOURCE_VERSION_CHANGED");
  boolean changedStructure=!Objects.equals(before.get("configId"),change.configId())||!Objects.equals(before.get("productForm"),change.productForm())||!Objects.equals(before.get("unitId"),change.unitId())||change.specificationMode()!=null&&!Objects.equals(before.get("specificationMode"),change.specificationMode());
  @SuppressWarnings("unchecked") var specs=(List<Map<String,Object>>)before.get("specifications");
  var current=specs.stream().filter(r->r.get("retiredAt")==null).map(r->(String)r.get("id")).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
  if(changedStructure&&!current.equals(new TreeSet<>(change.retiredSpecificationIds()==null?List.of():change.retiredSpecificationIds())))throw problem("PRODUCT_SPEC_EXIT_REQUIRED");
  if(!changedStructure&&change.retiredSpecificationIds()!=null&&!change.retiredSpecificationIds().isEmpty())throw invalid();
  if("ENABLED".equals(before.get("status"))||before.get("archivedAt")!=null)throw problem("PRODUCT_STRUCTURE_REQUIRES_DISABLED");
  if("LEGACY_PENDING".equals(before.get("structureState"))&&(!"CONFIRMED_NONE".equals(change.legacyDisposition())||!"CONFIRMED_NONE".equals(before.get("legacyDisposition"))))throw problem("PRODUCT_LEGACY_SPEC_REQUIRED");
 }
 private List<String> targetIssues(VerifiedTenantContext c,Map<String,Object> before,Migration change){
  var result=new ArrayList<String>();
  if(change.categoryId()==null)result.add("categoryId");if(change.configId()==null)result.add("categoryConfigId");if(change.productForm()==null)result.add("productForm");
  boolean required=Set.of("PHYSICAL","COMPOSITE").contains(Objects.toString(change.productForm(),""));
  if(change.configId()!=null)required|=Boolean.TRUE.equals(jdbc.queryForObject("SELECT service_unit_required FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Boolean.class,tenant(c),change.configId()));
  if(required&&change.unitId()==null)result.add("baseUnitId");
  var supplied=change.values().stream().map(Value::templateItemId).collect(java.util.stream.Collectors.toSet());
  if(change.configId()!=null)for(String item:jdbc.queryForList("SELECT BIN_TO_UUID(i.id) FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) AND ci.purpose<>'SPEC' AND i.required_flag=TRUE",String.class,tenant(c),change.configId()))if(!supplied.contains(item))result.add("attribute:"+item);
  return result;
 }
 @SuppressWarnings("unchecked")
 private static List<String> droppedValues(Map<String,Object> before,Migration change){
  var supplied=change.values().stream().map(Value::templateItemId).collect(java.util.stream.Collectors.toSet());
  return ((List<Map<String,Object>>)before.get("values")).stream().map(v->(String)v.get("templateItemId")).filter(v->!supplied.contains(v)).sorted().toList();
 }
 private String payloadDigest(Migration change){
  var canonical=new TreeMap<String,Object>();
  canonical.put("specificationMode",change.specificationMode());canonical.put("retiredSpecificationIds",change.retiredSpecificationIds()==null?List.of():change.retiredSpecificationIds().stream().sorted().toList());
  canonical.put("sourceVersion",change.sourceVersion());canonical.put("sourceStructureRevision",change.sourceStructureRevision());canonical.put("sourceCategoryConfigId",change.sourceCategoryConfigId());
  canonical.put("categoryId",change.categoryId());canonical.put("configId",change.configId());canonical.put("productForm",change.productForm());canonical.put("unitId",change.unitId());canonical.put("reason",change.reason());canonical.put("legacyDisposition",change.legacyDisposition());
  canonical.put("droppedItemIds",change.droppedItemIds().stream().sorted().toList());
  canonical.put("values",change.values().stream().sorted(Comparator.comparing(Value::templateItemId)).map(v->new Value(v.templateItemId(),v.value(),v.optionIds()==null?List.of():v.optionIds().stream().sorted().toList(),v.customValues()==null?List.of():v.customValues().stream().sorted().toList())).toList());
  return hash(json.writeValueAsString(canonical));
 }
 private String binding(VerifiedTenantContext c,String id,Migration change){
  var admission=new TreeMap<String,Object>();
  if(change.categoryId()!=null)admission.put("category",jdbc.queryForList("SELECT enabled,version FROM md_product_category WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),change.categoryId()));
  if(change.configId()!=null)admission.put("templates",jdbc.queryForList("SELECT BIN_TO_UUID(v.id) id,v.state,t.enabled,t.version FROM md_product_category_template b JOIN md_product_template_version v ON v.tenant_id=b.tenant_id AND v.id=b.template_version_id JOIN md_product_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id WHERE b.tenant_id=UUID_TO_BIN(?) AND b.config_id=UUID_TO_BIN(?) ORDER BY v.id",tenant(c),change.configId()));
  if(change.unitId()!=null)admission.put("unit",jdbc.queryForList("SELECT enabled,version FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),change.unitId()));
  return hash("product-structure:v1:"+tenant(c)+":"+actor(c)+":"+id+":"+payloadDigest(change)+":"+json.writeValueAsString(admission));
 }
 private static String hash(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private void clearValues(VerifiedTenantContext c,String id){
  jdbc.update("DELETE ch FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?)",tenant(c),id);
  jdbc.update("DELETE FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",tenant(c),id);
 }
 private static MasterData.Rejection problem(String code){return new MasterData.Rejection(409,code);}


 /**
  * 旧规格人工确认只追加审查历史，不完成结构、不改原文；后续仍须停用并完整迁移。
  * @param disposition 仅CONFIRMED_NONE或DEFERRED_SPEC
  * @param reason 1至1000字符的确认依据，不包含敏感凭据
  * @param version 商品当前CAS，非负；审查成功只推进version，不推进structure_revision
  */
 public record LegacyReview(String disposition,String reason,Long version){}
 /** 原读取权与结构迁移权双重校验，不能用普通编辑权改历史事实。 */
 public Map<String,Object> reviewLegacySpecification(VerifiedTenantContext c,String id,LegacyReview input){
  require(c,"migrate_structure");require(c,"read");uuid(id);
  if(input==null||input.version()==null||input.version()<0||input.disposition()==null||!Set.of("CONFIRMED_NONE","DEFERRED_SPEC").contains(input.disposition()))throw invalid();
  text(input.reason(),1000,true);
  return write(()->{
   var before=locked(c,id,input.version());ensureNotInApproval(c,id);
   if(before.get("legacyEnabled")==null||!"LEGACY_PENDING".equals(before.get("structureState")))throw problem("PRODUCT_LEGACY_REVIEW_NOT_APPLICABLE");
   jdbc.update("UPDATE md_product_v1 SET legacy_specification_disposition=? WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",input.disposition(),tenant(c),id);
   bump(c,id,input.version());event(c,id,"LEGACY_REVIEW",before,input.reason());return snapshot(c,id);
  });
 }


 /**
  * 暂存当前租户旧商品；只新增尚未暂存的ID，编码碰撞或已存在目标都不覆盖。
  * @param id 旧商品真实UUID；切换写入口前仍须冻结源写入并完整对账
  * @return 完整原文/审计保留的LEGACY_PENDING档案；不是新模型启用验收
  */
 public Map<String,Object> stageLegacy(VerifiedTenantContext c,String id){
  require(c,"stage_legacy");require(c,"read");uuid(id);
  return write(()->{
   var source=jdbc.queryForList("SELECT BIN_TO_UUID(id) id FROM md_product WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",tenant(c),id);
   if(source.isEmpty())throw changed();
   if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Integer.class,tenant(c),id)!=0)throw problem("PRODUCT_LEGACY_ALREADY_STAGED");
   // 显式列出审计以绕过缺省填充，NULL原样保留；源version只作对账，不伪造旧change事件。
   jdbc.update("INSERT INTO md_product_v1(id,tenant_id,code,name,barcode,status,structure_state,version,legacy_enabled_snapshot,legacy_specification_original,legacy_specification_disposition,legacy_unit_original,legacy_source_version,legacy_name_original,legacy_updated_by_original,legacy_updated_at_original,created_by,created_at,updated_by,updated_at) SELECT id,tenant_id,code,name,barcode,CASE WHEN enabled THEN 'ENABLED' ELSE 'DISABLED' END,'LEGACY_PENDING',version,enabled,specification,'UNREVIEWED',unit,version,name,updated_by,updated_at,created_by,created_at,updated_by,updated_at FROM md_product WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),id);
   return snapshot(c,id);
  });
 }
 /**
  * 只读对账必须覆盖旧正文、版本与四审计，不能仅比较总数。
  * @return 每个旧商品missing/matched/sourceChanged及当前租户新增旧商品缺口；不自动覆盖已人工审查的数据
  */
 public Object reconcileLegacy(VerifiedTenantContext c,int page,int size){
  require(c,"stage_legacy");require(c,"read");if(page<1||page>1000000||size<1||size>100)throw invalid();
  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(o.id) id,o.code,CASE WHEN p.id IS NULL THEN 'MISSING' WHEN p.legacy_source_version<>o.version OR NOT(BINARY p.code<=>BINARY o.code) OR NOT(BINARY p.legacy_name_original<=>BINARY o.name) OR NOT(BINARY p.barcode<=>BINARY o.barcode) OR NOT(BINARY p.legacy_specification_original<=>BINARY o.specification) OR NOT(BINARY p.legacy_unit_original<=>BINARY o.unit) OR NOT(p.legacy_enabled_snapshot<=>o.enabled) OR NOT(p.created_by<=>o.created_by) OR NOT(p.created_at<=>o.created_at) OR NOT(p.legacy_updated_by_original<=>o.updated_by) OR NOT(p.legacy_updated_at_original<=>o.updated_at) THEN 'SOURCE_CHANGED' ELSE 'MATCHED' END result FROM md_product o LEFT JOIN md_product_v1 p ON p.tenant_id=o.tenant_id AND p.id=o.id WHERE o.tenant_id=UUID_TO_BIN(?) ORDER BY o.code,o.id LIMIT ? OFFSET ?",tenant(c),size,(page-1)*size);
  return Map.of("rows",rows,"total",jdbc.queryForObject("SELECT COUNT(*) FROM md_product WHERE tenant_id=UUID_TO_BIN(?)",Long.class,tenant(c)));
 }

 private Map<String,Object> row(VerifiedTenantContext c,String id,boolean lock){
  var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,short_name shortName,description,brand,origin_text origin,BIN_TO_UUID(category_id) categoryId,BIN_TO_UUID(category_config_id) configId,product_form productForm,specification_mode specificationMode,BIN_TO_UUID(base_unit_id) unitId,status,structure_state structureState,draft_archived_at archivedAt,version,structure_revision structureRevision,legacy_enabled_snapshot legacyEnabled,legacy_specification_original legacySpecificationOriginal,legacy_specification_disposition legacyDisposition,barcode,legacy_unit_original legacyUnitOriginal,legacy_source_version legacySourceVersion,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),(r,n)->{
   var m=new LinkedHashMap<String,Object>();for(String col:List.of("id","code","name","shortName","description","brand","origin","categoryId","configId","productForm","specificationMode","unitId","status","structureState","legacySpecificationOriginal","legacyDisposition","barcode","legacyUnitOriginal","createdBy","updatedBy"))m.put(col,r.getString(col));
   for(String col:List.of("archivedAt","createdAt","updatedAt"))m.put(col,r.getTimestamp(col)==null?null:r.getTimestamp(col).toInstant().toString());
   m.put("legacySourceVersion",r.getObject("legacySourceVersion"));m.put("legacyEnabled",r.getObject("legacyEnabled"));m.put("version",r.getLong("version"));m.put("structureRevision",r.getLong("structureRevision"));return m;
  },tenant(c),id);
  if(rows.isEmpty())throw changed();return rows.getFirst();
 }
 private Map<String,Object> snapshot(VerifiedTenantContext c,String id){
  var m=row(c,id,false);
  m.put("approvalPending",jdbc.queryForObject("SELECT COUNT(*)>0 FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND state='SUBMITTED'",Boolean.class,tenant(c),id));
  m.put("specifications",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,mode,name,enabled,retired_at retiredAt,combination_canonical combination FROM md_product_specification WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY id",tenant(c),id));
  m.put("skus",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(specification_id) specificationId,code,enabled,version FROM md_product_sku WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY id",tenant(c),id));
  m.put("components",jdbc.queryForList("SELECT BIN_TO_UUID(c.component_product_id) componentId,p.code componentCode,p.name componentName,CAST(c.quantity AS CHAR) quantity,c.component_version componentVersion,c.enabled FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=UUID_TO_BIN(?) AND c.product_id=UUID_TO_BIN(?) ORDER BY c.id",tenant(c),id));
  m.put("images",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,HEX(content_hash) contentHash,width_px width,height_px height FROM md_product_image WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY id",tenant(c),id));
  m.put("values",jdbc.queryForList("SELECT BIN_TO_UUID(v.template_item_id) templateItemId,BIN_TO_UUID(v.attribute_revision_id) revisionId,r.name attributeName,r.data_type dataType,v.text_value textValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,CAST(v.date_value AS CHAR) dateValue FROM md_product_v1_attribute_value v JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id WHERE v.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?) ORDER BY v.id",tenant(c),id));
  m.put("choices",jdbc.queryForList("SELECT BIN_TO_UUID(v.template_item_id) templateItemId,BIN_TO_UUID(o.id) optionId,o.option_code optionCode,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE ch.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?)",tenant(c),id));
  var slots=new LinkedHashMap<String,Object>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  if(m.get("configId")!=null){
   m.put("categoryConfiguration",jdbc.queryForMap("SELECT revision_no revision,display_path_snapshot path,service_unit_required serviceUnitRequired FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),m.get("configId")));
   jdbc.query("SELECT purpose,BIN_TO_UUID(template_version_id) versionId FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(?) AND config_id=UUID_TO_BIN(?)",rs->{slots.put(rs.getString("purpose"),rs.getString("versionId"));},tenant(c),m.get("configId"));
  }
  m.put("templates",slots);m.put("schemaVersion",1);
  if(m.get("unitId")!=null)m.put("baseUnit",jdbc.queryForMap("SELECT code,name,dimension_code dimensionCode,precision_scale precisionScale FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),m.get("unitId")));
  m.put("completionIssues",issues(c,m));
  var audit=new LinkedHashMap<String,Object>();for(String k:List.of("createdBy","createdAt","updatedBy","updatedAt"))audit.put(k,m.get(k));m.put("audit",audit);return m;
 }
 private Map<String,Object> locked(VerifiedTenantContext c,String id,long version){if(version<0)throw invalid();var before=row(c,id,true);if(((Number)before.get("version")).longValue()!=version)throw changed();return snapshot(c,id);}
 private static void mutable(Map<String,Object> row){if(row.get("archivedAt")!=null||"LEGACY_PENDING".equals(row.get("structureState")))throw changed();}
 /** 处理中快照冻结所有商品扩展和普通写；审批结果不会自动授予启用权限。 */
 void ensureNotInApproval(VerifiedTenantContext c,String id){if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND state='SUBMITTED'",Integer.class,tenant(c),id)>0)throw problem("PRODUCT_APPROVAL_IN_PROGRESS");}
 private void bump(VerifiedTenantContext c,String id,long version){
  if(jdbc.update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",actor(c),tenant(c),id,version)!=1)throw changed();
 }
 /** 审核提交在父锁下重验动态必填和组成版本，不能信任上次保存的完整性标记。 */
 void requireCompleteForApproval(VerifiedTenantContext c,String id){if(!issues(c,row(c,id,false)).isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");}
 /** 同领域图片扩展在父锁事务内捕获快照；调用方已复核独立写权限，不能作为HTTP自由查询入口。 */
 Map<String,Object> extensionSnapshot(VerifiedTenantContext c,String id){return snapshot(c,id);}
 /** 同父聚合事务追加扩展事件，失败必须与扩展正文及CAS一起回滚。 */
 void extensionEvent(VerifiedTenantContext c,String id,String type,Map<String,Object> before){refreshCompleteness(c,id);event(c,id,type,before);}
 private String event(VerifiedTenantContext c,String id,String type,Map<String,Object> before){return event(c,id,type,before,null);}
 private String event(VerifiedTenantContext c,String id,String type,Map<String,Object> before,String reason){
  var after=snapshot(c,id);if(reason!=null)after.put("changeReason",reason);String changeId=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO md_product_v1_change(id,tenant_id,product_id,to_version,event_type,before_snapshot,after_snapshot,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",changeId,tenant(c),id,after.get("version"),type,before==null?null:json.writeValueAsString(before),json.writeValueAsString(after),actor(c),actor(c));return changeId;
 }
 private void validateReferences(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh){validateReferences(c,category,config,form,unit,fresh,true);}
 private void validateReferences(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh,boolean locks){
  if(category!=null){uuid(category);var rows=jdbc.queryForList("SELECT enabled FROM md_product_category WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(locks?" FOR SHARE":""),tenant(c),category);if(rows.isEmpty()||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw invalid();}
  if(config!=null){uuid(config);if(category==null||jdbc.queryForObject("SELECT COUNT(*) FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?)",Integer.class,tenant(c),config,category)!=1)throw invalid();
   if(fresh){var versions=jdbc.queryForList("SELECT v.state,t.enabled FROM md_product_category_template b JOIN md_product_template_version v ON v.tenant_id=b.tenant_id AND v.id=b.template_version_id JOIN md_product_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id WHERE b.tenant_id=UUID_TO_BIN(?) AND b.config_id=UUID_TO_BIN(?) ORDER BY v.id"+(locks?" FOR SHARE":""),tenant(c),config);for(var v:versions)if(!"PUBLISHED".equals(v.get("state"))||!Boolean.TRUE.equals(v.get("enabled")))throw invalid();}
  }
  if(form!=null&&!Set.of("PHYSICAL","SERVICE","COMPOSITE").contains(form))throw invalid();
  if(unit!=null){uuid(unit);var units=jdbc.queryForList("SELECT enabled FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(locks?" FOR SHARE":""),tenant(c),unit);if(units.size()!=1||!Boolean.TRUE.equals(units.getFirst().get("enabled")))throw invalid();}
 }
 private List<String> issues(VerifiedTenantContext c,Map<String,Object> row){
  var issues=new ArrayList<String>();
  if(row.get("categoryId")==null)issues.add("categoryId");if(row.get("configId")==null)issues.add("categoryConfigId");if(row.get("productForm")==null)issues.add("productForm");
  boolean unitRequired=Set.of("PHYSICAL","COMPOSITE").contains(Objects.toString(row.get("productForm"),""));
  if(row.get("configId")!=null)unitRequired|=Boolean.TRUE.equals(jdbc.queryForObject("SELECT service_unit_required FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Boolean.class,tenant(c),row.get("configId")));
  if(unitRequired&&row.get("unitId")==null)issues.add("baseUnitId");
  if(row.get("configId")!=null)issues.addAll(jdbc.queryForList("SELECT BIN_TO_UUID(i.id) FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) AND ci.purpose<>'SPEC' AND i.required_flag=TRUE AND NOT EXISTS(SELECT 1 FROM md_product_v1_attribute_value v WHERE v.tenant_id=ci.tenant_id AND v.product_id=UUID_TO_BIN(?) AND v.template_item_id=i.id)",String.class,tenant(c),row.get("configId"),row.get("id")).stream().map(x->"attribute:"+x).toList());
  String pid=Objects.toString(row.get("id"));
  if("STANDARD".equals(row.get("specificationMode"))&&jdbc.queryForObject("SELECT COUNT(*) FROM md_product_specification WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND retired_at IS NULL AND enabled=TRUE",Integer.class,tenant(c),pid)==0)issues.add("specifications");
  if("COMPOSITE".equals(row.get("productForm"))){var components=jdbc.queryForList("SELECT p.status,p.version,c.component_version FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=UUID_TO_BIN(?) AND c.product_id=UUID_TO_BIN(?) AND c.enabled=TRUE",tenant(c),pid);if(components.isEmpty()||components.stream().anyMatch(x->!"ENABLED".equals(x.get("status"))||!x.get("version").equals(x.get("component_version"))))issues.add("components");}
  return issues;
 }
 private List<String> refreshCompleteness(VerifiedTenantContext c,String id){
  var row=row(c,id,false);var issues=issues(c,row);if("ENABLED".equals(row.get("status"))&&!"LEGACY_PENDING".equals(row.get("structureState"))&&!issues.isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");String state="LEGACY_PENDING".equals(row.get("structureState"))?"LEGACY_PENDING":issues.isEmpty()?"COMPLETE":"INCOMPLETE";
  jdbc.update("UPDATE md_product_v1 SET structure_state=?,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",state,actor(c),tenant(c),id);return issues;
 }
 private record ValueHistory(Map<String,Map<String,Object>> values,Map<String,Map<String,Object>> choices){}
 private ValueHistory valueHistory(VerifiedTenantContext c,String id){
  var originals=new HashMap<String,Map<String,Object>>();
  for(var old:jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(template_item_id) item,BIN_TO_UUID(created_by) creator,created_at FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",tenant(c),id))originals.put((String)old.get("item"),old);
  var oldChoices=new HashMap<String,Map<String,Object>>();
  for(var old:jdbc.queryForList("SELECT BIN_TO_UUID(ch.id) id,BIN_TO_UUID(v.template_item_id) item,BIN_TO_UUID(ch.option_id) optionId,ch.custom_text customText,BIN_TO_UUID(ch.created_by) creator,ch.created_at FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?)",tenant(c),id))oldChoices.put(old.get("item")+":"+(old.get("optionId")==null?"custom:"+old.get("customText"):old.get("optionId")),old);

  return new ValueHistory(originals,oldChoices);
 }
 private void saveValues(VerifiedTenantContext c,String id,String config,List<Value> values){saveValues(c,id,config,values,null);}
 private void saveValues(VerifiedTenantContext c,String id,String config,List<Value> values,ValueHistory preserved){
  var preparedValues=prepareValues(c,config,values);var history=preserved==null?valueHistory(c,id):preserved;
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

 /** 类型化校验只读复用，预览不插入临时值或使用写后回滚模拟校验。 */
 private record PreparedValue(Value source,String revision,String type,Object text,Object integer,Object decimal,Object bool,Object date){}
 private List<PreparedValue> prepareValues(VerifiedTenantContext c,String config,List<Value> values){
  if(values==null)values=List.of();if(values.size()>200||config==null&&!values.isEmpty())throw invalid();
  var seen=new HashSet<String>();var result=new ArrayList<PreparedValue>();
  for(Value value:values){
   if(value==null)throw invalid();uuid(value.templateItemId());if(!seen.add(value.templateItemId()))throw invalid();
   var definitions=jdbc.queryForList("SELECT BIN_TO_UUID(ci.attribute_revision_id) revision,r.data_type,r.numeric_scale,CAST(r.validation_json AS CHAR) validation,i.allow_custom FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) AND ci.purpose<>'SPEC' AND ci.template_item_id=UUID_TO_BIN(?)",tenant(c),config,value.templateItemId());
   if(definitions.size()!=1)throw invalid();var definition=definitions.getFirst();String type=(String)definition.get("data_type"),revision=(String)definition.get("revision");
   Object text=null,integer=null,decimal=null,bool=null,date=null;List<String> options=value.optionIds()==null?List.of():value.optionIds();List<String> customs=value.customValues()==null?List.of():value.customValues();
   if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&!customs.isEmpty())throw invalid();
   if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&value.value()==null)throw invalid();
   try{switch(type){
    case "TEXT" -> {text(value.value(),2000,true);if(!options.isEmpty())throw invalid();text=value.value();}
    case "INTEGER" -> {if(value.value()==null||!value.value().matches("-?(0|[1-9][0-9]*)"))throw invalid();integer=Long.parseLong(value.value());}
    case "DECIMAL" -> {if(value.value()==null||!value.value().matches("-?[0-9]+(\\.[0-9]+)?"))throw invalid();var number=new BigDecimal(value.value());int scale=((Number)definition.get("numeric_scale")).intValue();if(number.scale()>scale||number.precision()-number.scale()>18)throw invalid();decimal=number;}
    case "BOOLEAN" -> {if(!Set.of("true","false").contains(value.value()))throw invalid();bool=Boolean.parseBoolean(value.value());}
    case "DATE" -> {date=LocalDate.parse(value.value());}
    case "SINGLE_SELECT","MULTI_SELECT" -> {if(value.value()!=null||options.size()+customs.size()==0||options.size()+customs.size()>50||type.equals("SINGLE_SELECT")&&options.size()+customs.size()!=1||new HashSet<>(options).size()!=options.size())throw invalid();}
    default -> throw invalid();
   }}catch(IllegalArgumentException|java.time.DateTimeException e){throw invalid();}
   if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&!options.isEmpty())throw invalid();

   for(String option:options){uuid(option);if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",Integer.class,tenant(c),revision,option)!=1)throw invalid();}
   if(!customs.isEmpty()){if(!Boolean.TRUE.equals(definition.get("allow_custom"))||new HashSet<>(customs).size()!=customs.size())throw invalid();for(String custom:customs)text(custom,2000,true);}
   ProductAttributeRules.value(type,(String)definition.get("validation"),value.value(),options.size()+customs.size());
   result.add(new PreparedValue(value,revision,type,text,integer,decimal,bool,date));
  }
  return result;
 }

 private <T>T write(java.util.function.Supplier<T> work){try{return tx.execute(x->work.get());}catch(DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_V1_CONFLICT");}}
 private void require(VerifiedTenantContext c,String action){if(c==null||c.accountId()==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+action))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private static void validate(Input i){if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();text(i.name(),200,true);text(i.shortName(),100,false);text(i.brand(),200,false);text(i.origin(),200,false);text(i.description(),20000,false);text(i.barcode(),500,false);}
 private static void text(String value,int max,boolean required){if(value==null){if(required)throw invalid();return;}if(required&&value.isBlank()||value.length()>max||value.chars().anyMatch(x->Character.isISOControl(x)&&x!='\n'&&x!='\r'&&x!='\t'))throw invalid();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");}
}
