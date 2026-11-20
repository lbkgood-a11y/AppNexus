package com.appnexus.masterdata.product;
import com.appnexus.masterdata.adapter.jdbc.ProductStructureServiceSqlMapper;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 规格、可选SKU和组合结构领域服务；父锁/CAS及追加历史同事务，不触及库存、定价或拆装。 */
@Service public final class ProductStructureService {
 private final ProductStructureServiceSqlMapper statements;

 private final AccessAuthorization access;private final TransactionTemplate tx;private final ProductV1Service products;
 /** @param jdbc UTC数据源 @param access 公开授权端口 @param manager 同数据源事务 @param products 同领域快照/完整性/历史执行器 */
 public ProductStructureService(AccessAuthorization access,PlatformTransactionManager manager,ProductV1Service products,ProductStructureServiceSqlMapper statements){this.statements=statements;this.access=access;this.tx=new TransactionTemplate(manager);this.products=products;}
 /** @param name 规格名称1至200字符 @param description 自定义描述最大2000字符 @param values 标准规格固定SPEC模板项值，标量或固定选项UUID @param version 商品当前CAS */
 public record Specification(String name,String description,List<SpecValue> values,Long version){}
 /** @param templateItemId 固定SPEC项UUID @param value INTEGER/DECIMAL/BOOLEAN标量或单选option UUID */
 public record SpecValue(String templateItemId,String value,List<String> optionIds,List<String> customValues){
  /** 原标准规格标量/单选调用保持兼容，CUSTOM可显式提交枚举列表；单选不得同时两种表示。 */
  public SpecValue(String templateItemId,String value){this(templateItemId,value,List.of(),List.of());}
 }
 /** @param specificationId 已保存当前有效规格UUID @param code SKU独立编码，创建后不可修改 @param version 商品CAS */
 public record Sku(String specificationId,String code,Long version){}
 /** @param componentId 组成商品UUID @param quantity 组成商品基础单位下正数精确数量 @param componentVersion 当前组成商品版本 @param version 组合父商品CAS */
 public record Component(String componentId,String quantity,Long componentVersion,Long version){}
 /** @param enabled 目标启停状态 @param rowVersion 子记录当前CAS @param version 父商品CAS */
 public record State(Boolean enabled,Long rowVersion,Long version){}
 /** 查询规格/SKU/组成，effective按商品与规格分层状态计算，不把SKU.enabled当可用事实。 */
 public Object read(VerifiedTenantContext c,String id){require(c,"read");var p=parent(c,id,false,null);var out=new LinkedHashMap<String,Object>();out.put("mode",p.get("specification_mode"));out.put("form",p.get("product_form"));out.put("version",p.get("version"));out.put("specifications",statements.statement1(tenant(c),id));out.put("skus",statements.statement2(tenant(c),id));out.put("components",statements.statement3(tenant(c),id));attachSpecificationValues(c,out);attachAudit(c,out,"specifications","md_product_specification");attachAudit(c,out,"skus","md_product_sku");attachAudit(c,out,"components","md_product_component");return out;}
 /** 标准规格组合值按模板稳定顺序规范化；哈希仅用于唯一索引，碰撞仍拒绝并保留原串。 */
 public Object createSpecification(VerifiedTenantContext c,String product,Specification i){require(c,"manage_structure");if(i==null||i.version()==null||i.name()==null||i.name().isBlank()||i.name().length()>200||i.description()!=null&&i.description().length()>2000)throw invalid();return write(()->{var p=parent(c,product,true,i.version());String mode=(String)p.get("specification_mode"),config=(String)p.get("configId");if(!Set.of("STANDARD","CUSTOM").contains(mode)||config==null)throw invalid();var before=products.extensionSnapshot(c,product);var values=i.values()==null?List.<SpecValue>of():i.values();if(values.size()>200)throw invalid();var prepared=new ArrayList<Object[]>();var canonical=new StringBuilder();
 if(mode.equals("CUSTOM")){if(values.isEmpty()&&(i.description()==null||i.description().isBlank()))throw invalid();}
 else {var definitions=statements.statement4(tenant(c),config);if(definitions.isEmpty()||definitions.size()!=values.size())throw invalid();var supplied=new HashMap<String,String>();for(var v:values){if(v==null||v.value()==null||supplied.put(v.templateItemId(),v.value())!=null)throw invalid();}
 for(var d:definitions){String item=(String)d.get("item"),revision=(String)d.get("revision"),type=(String)d.get("type"),raw=supplied.get(item),normal=raw;Object integer=null,decimal=null,bool=null,option=null;if(raw==null)throw invalid();try{switch(type){case "INTEGER"->{if(!raw.matches("-?(0|[1-9][0-9]*)"))throw invalid();integer=Long.parseLong(raw);normal=integer.toString();}case "DECIMAL"->{if(!raw.matches("-?[0-9]+([.][0-9]+)?"))throw invalid();var n=new BigDecimal(raw);if(n.scale()>((Number)d.get("scale")).intValue()||n.precision()-n.scale()>18)throw invalid();decimal=n;normal=n.stripTrailingZeros().toPlainString();}case "BOOLEAN"->{if(!Set.of("true","false").contains(raw))throw invalid();bool=Boolean.parseBoolean(raw);}case "SINGLE_SELECT"->{uuid(raw);var opts=statements.statement5(tenant(c),revision,raw);if(opts.size()!=1)throw invalid();option=raw;normal=(String)opts.getFirst().get("option_code");}default->throw invalid();}}catch(NumberFormatException e){throw invalid();}ProductAttributeRules.value(type,(String)d.get("rules"),Set.of("INTEGER","DECIMAL","BOOLEAN").contains(type)?raw:null,type.equals("SINGLE_SELECT")?1:0);canonical.append(item).append(':').append(revision).append(':').append(type).append(':').append(normal.length()).append(':').append(normal).append(';');prepared.add(new Object[]{item,revision,type,integer,decimal,bool,option});}}
 String id=UUID.randomUUID().toString(),signature=mode.equals("STANDARD")?canonical.toString():null;byte[] hash=null;if(signature!=null)try{hash=java.security.MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}statements.statement6(id,tenant(c),product,config,mode,i.name().trim(),i.description(),signature,hash,actor(c),actor(c));for(var v:prepared)statements.statement7(UUID.randomUUID().toString(),tenant(c),id,config,v[0],v[1],v[2],v[3],v[4],v[5],v[6],actor(c),actor(c));if(mode.equals("CUSTOM"))saveCustomValues(c,id,config,values);finish(c,product,i.version(),"SPEC_CREATE",before);return Map.of("id",id);});}
 /** 自定义SPEC使用固定修订逐项校验；必填只在建立规格记录时校验，不强迫商品预建规格或SKU。 */
 private void saveCustomValues(VerifiedTenantContext c,String specification,String config,List<SpecValue> values){
  var definitions=statements.statement8(tenant(c),config);
  var supplied=new HashMap<String,SpecValue>();for(var value:values){if(value==null||value.templateItemId()==null||supplied.put(value.templateItemId(),value)!=null)throw invalid();}
  for(var d:definitions){String item=(String)d.get("item"),revision=(String)d.get("revision"),type=(String)d.get("type");var value=supplied.remove(item);if(value==null){if(Boolean.TRUE.equals(d.get("requiredFlag")))throw invalid();continue;}
   String raw=value.value();List<String> options=value.optionIds()==null?List.of():value.optionIds(),customs=value.customValues()==null?List.of():value.customValues();Object text=null,date=null,integer=null,decimal=null,bool=null,option=null;
   if(options.size()+customs.size()>50||options.stream().anyMatch(Objects::isNull)||customs.stream().anyMatch(Objects::isNull)||new HashSet<>(options).size()!=options.size()||new HashSet<>(customs).size()!=customs.size())throw invalid();
   try{switch(type){
    case "TEXT"->{if(raw==null||raw.isBlank()||raw.length()>2000)throw invalid();text=raw;}
    case "DATE"->{if(raw==null||!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();date=java.time.LocalDate.parse(raw);}
    case "INTEGER"->{if(raw==null||!raw.matches("-?(0|[1-9][0-9]*)"))throw invalid();integer=Long.parseLong(raw);}
    case "DECIMAL"->{if(raw==null||!raw.matches("-?(0|[1-9][0-9]*)([.][0-9]+)?"))throw invalid();var n=new BigDecimal(raw);if(n.scale()>((Number)d.get("scale")).intValue()||n.precision()-n.scale()>18)throw invalid();decimal=n;}
    case "BOOLEAN"->{if(!Set.of("true","false").contains(Objects.toString(raw,"")))throw invalid();bool=Boolean.parseBoolean(raw);}
    case "SINGLE_SELECT","MULTI_SELECT"->{if(raw!=null||options.size()+customs.size()<1||type.equals("SINGLE_SELECT")&&options.size()+customs.size()!=1)throw invalid();}
    default->throw invalid();
   }}catch(NumberFormatException|java.time.DateTimeException e){throw invalid();}
   boolean enumeration=type.equals("SINGLE_SELECT")||type.equals("MULTI_SELECT");if(!enumeration&&(!options.isEmpty()||!customs.isEmpty()))throw invalid();
   for(String selected:options){uuid(selected);if(statements.statement9(tenant(c),revision,selected)!=1)throw invalid();}
   if(!customs.isEmpty()&&!Boolean.TRUE.equals(d.get("allowCustom")))throw invalid();for(String custom:customs)if(custom.isBlank()||custom.length()>2000||custom.chars().anyMatch(Character::isISOControl))throw invalid();
   if(raw!=null&&type.equals("TEXT")&&raw.chars().anyMatch(ch->Character.isISOControl(ch)&&ch!='\n'&&ch!='\r'&&ch!='\t'))throw invalid();
   ProductAttributeRules.value(type,(String)d.get("rules"),enumeration?null:raw,options.size()+customs.size());String valueId=UUID.randomUUID().toString();
   statements.statement10(valueId,tenant(c),specification,config,item,revision,type,text,date,integer,decimal,bool,actor(c),actor(c));
   for(String selected:options)insertChoice(c,valueId,revision,type,selected,null);for(String custom:customs)insertChoice(c,valueId,revision,type,null,custom);
  }
  if(!supplied.isEmpty())throw invalid();
 }
 private void insertChoice(VerifiedTenantContext c,String value,String revision,String type,String option,String custom){statements.statement11(UUID.randomUUID().toString(),tenant(c),value,revision,type,option,custom,actor(c),actor(c));}
 /** 仅原商品读取权限内投影规格值和固定名称；包含历史退役规格，不追最新模板。 */
 @SuppressWarnings("unchecked") private void attachSpecificationValues(VerifiedTenantContext c,Map<String,Object> output){for(var spec:(List<Map<String,Object>>)output.get("specifications")){var values=statements.statement12(tenant(c),spec.get("id"));for(var value:values){var choices=statements.statement13(tenant(c),value.get("id"));if(value.get("optionId")!=null)choices.addAll(statements.statement14(tenant(c),value.get("optionId")));value.put("choices",choices);}spec.put("values",values);}}
 /** 只有显式动作才创建SKU；父子归属复合外键兜底，SKU身份创建后不可修改。 */
 public Object createSku(VerifiedTenantContext c,String product,Sku i){require(c,"manage_structure");if(i==null||i.version()==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();uuid(i.specificationId());return write(()->{parent(c,product,true,i.version());var before=products.extensionSnapshot(c,product);if(statements.statement15(tenant(c),product,i.specificationId())!=1)throw invalid();String id=UUID.randomUUID().toString();statements.statement16(id,tenant(c),product,i.specificationId(),i.code(),actor(c),actor(c));finish(c,product,i.version(),"SKU_CREATE",before);return Map.of("id",id);});}
 /** 组合写入共用租户图锁，防止并发互加形成环；数量按组成基础单位精度，不自动换算。 */
 public Object addComponent(VerifiedTenantContext c,String product,Component i){require(c,"manage_structure");if(i==null||i.version()==null||i.componentVersion()==null||i.quantity()==null||!i.quantity().matches("(0|[1-9][0-9]{0,17})([.][0-9]{1,12})?"))throw invalid();uuid(i.componentId());var quantity=new BigDecimal(i.quantity());if(quantity.signum()<=0||product.equals(i.componentId()))throw invalid();return write(()->{graphLock(c);var p=parent(c,product,true,i.version());if(!"COMPOSITE".equals(p.get("product_form")))throw invalid();var before=products.extensionSnapshot(c,product);var child=parent(c,i.componentId(),false,null);if(!"ENABLED".equals(child.get("status"))||!i.componentVersion().equals(((Number)child.get("version")).longValue())||child.get("unitId")==null)throw invalid();Integer scale=statements.statement17(tenant(c),child.get("unitId"));if(scale==null||quantity.scale()>scale)throw invalid();var seen=new HashSet<String>();var queue=new ArrayDeque<String>();queue.add(i.componentId());while(!queue.isEmpty()){String node=queue.remove();if(node.equals(product)||seen.size()>10000)throw invalid();if(!seen.add(node))continue;queue.addAll(statements.statement18(tenant(c),node));}var existing=statements.statement19(tenant(c),product,i.componentId());if(!existing.isEmpty()){if(Boolean.TRUE.equals(existing.getFirst().get("enabled")))throw invalid();String reused=(String)existing.getFirst().get("id");statements.statement20(quantity,i.componentVersion(),actor(c),tenant(c),reused);finish(c,product,i.version(),"COMPONENT_REPLACE",before);return Map.of("id",reused);}String id=UUID.randomUUID().toString();statements.statement21(id,tenant(c),product,i.componentId(),quantity,i.componentVersion(),actor(c),actor(c));finish(c,product,i.version(),"COMPONENT_ADD",before);return Map.of("id",id);});}
 /** 子对象启停按父CAS与子CAS原子修改；不能恢复退役规格，不能跨商品操作SKU。 */
 public Object state(VerifiedTenantContext c,String product,String kind,String id,State i){require(c,"manage_structure");uuid(id);if(i==null||i.version()==null||i.rowVersion()==null||i.enabled()==null)throw invalid();String table=switch(kind){case "specification"->"md_product_specification";case "sku"->"md_product_sku";case "component"->"md_product_component";default->throw invalid();};return write(()->{if(kind.equals("component"))graphLock(c);parent(c,product,true,i.version());if(kind.equals("component")&&i.enabled())throw invalid();var before=products.extensionSnapshot(c,product);int n=statements.state(kind,i.enabled(),actor(c),tenant(c),product,id,i.rowVersion());if(n!=1)throw changed();finish(c,product,i.version(),"STRUCTURE_STATE",before);return Map.of("ok",true);});}
 /** 已授权父商品内批量投影四审计，不接受外部表名，查询使用可信租户与已返回行ID。 */
 @SuppressWarnings("unchecked") private void attachAudit(VerifiedTenantContext c,Map<String,Object> output,String key,String table){if(!Set.of("md_product_specification","md_product_sku","md_product_component").contains(table))throw new IllegalArgumentException();for(var row:(List<Map<String,Object>>)output.get(key)){var audit=statements.audit(table,tenant(c),row.get("id")).stream().map(raw->{var result=new LinkedHashMap<String,Object>(raw);for(String field:List.of("createdAt","updatedAt")){Object value=result.get(field);if(value instanceof java.sql.Timestamp time)result.put(field,time.toInstant().toString());}return result;}).toList();if(!audit.isEmpty())row.put("audit",audit.getFirst());}}
 private void graphLock(VerifiedTenantContext c){statements.statement22(tenant(c),actor(c),actor(c));statements.statement23(tenant(c));}
 private Map<String,Object> parent(VerifiedTenantContext c,String id,boolean lock,Long version){products.authorizeRecord(c,id,lock?"manage_structure":"read",lock);uuid(id);var rows=statements.parent(tenant(c),id,lock);if(rows.size()!=1)throw changed();var p=rows.getFirst();if(lock)products.ensureNotInApproval(c,id);if(lock&&(version==null||version<0||((Number)p.get("version")).longValue()!=version||"ENABLED".equals(p.get("status"))||p.get("archived")!=null||"LEGACY_PENDING".equals(p.get("structure"))))throw changed();return p;}
 private void finish(VerifiedTenantContext c,String id,long version,String event,Map<String,Object> before){if(statements.statement24(actor(c),tenant(c),id,version)!=1)throw changed();products.extensionEvent(c,id,event,before);}
 private void require(VerifiedTenantContext c,String action){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+action))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> action){try{return tx.execute(s->action.get());}catch(org.springframework.dao.DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_STRUCTURE_CONFLICT");}}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_STRUCTURE_INVALID");}private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"MASTER_DATA_CHANGED");}
}
