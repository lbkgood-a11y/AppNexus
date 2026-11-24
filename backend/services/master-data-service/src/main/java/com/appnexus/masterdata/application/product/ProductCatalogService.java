package com.appnexus.masterdata.application.product;

import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogPages.UnitPage;
import com.appnexus.masterdata.api.product.ProductCatalogPages.CategoryPage;

import static com.appnexus.masterdata.api.product.ProductCatalogData.*;

/**
 * 商品单位、分类和不可变空配置的领域执行器。
 * 所有SQL值参数绑定，租户/账号仅来自可信上下文；树写入使用租户锚点行锁直到提交，
 * 防止两个事务分别校验通过却互相移入。分类移动不修改任何历史发布路径或配置。
 */
public final class ProductCatalogService implements com.appnexus.masterdata.api.product.ProductCatalogManagement {
 private final ProductCatalogRepository statements;

  
  private final ScopedWriteGate writes;
  private final AccessAuthorization access;
  private final ProductCatalogTransaction tx;
  private static final List<String> TEMPLATE_SLOTS=List.of("BASIC","SPEC","QUALITY","EXTENSION","SKU","BATCH");
  private static final Set<String> PRODUCT_SLOTS=Set.of("BASIC","SPEC","QUALITY","EXTENSION");

  /** 商品目录用例装配。
   * @param access 权威租户功能授权公开端口
   * @param transaction 同数据源完整事务及原冲突保护范围
   * @param statements 固定目录仓储，强制可信租户和固定对象映射
   */
  public ProductCatalogService(AccessAuthorization access,ProductCatalogTransaction transaction,ProductCatalogRepository statements,ScopedWriteGate writes){this.writes=writes;this.statements=statements;
    this.access=access;this.tx=transaction;
  }

  
  

  /** @param c 可信上下文 @param keyword 字面关键词，最大200字符 @param page 1起页码 @param size 1至100条 @return 真实单位分页 */
  public UnitPage units(VerifiedTenantContext c,String keyword,int page,int size,String group){
    require(c,"productunit","read");page(keyword,page,size);
    if(group!=null&&group.length()>64)throw invalid("PRODUCT_CATALOG_INVALID");
    long total=statements.unitCount(tenant(c),keyword.trim(),group);
    var rows=statements.units(tenant(c),keyword.trim(),group,size,(page-1)*size).stream().map(ProductCatalogService::unit).toList();
    return new UnitPage(rows,total);
  }

  /** {@inheritDoc} */
  public List<Map<String,Object>> unitGroups(VerifiedTenantContext c){
    require(c,"productunit","read");
    return statements.unitGroups(tenant(c));
  }

  /** {@inheritDoc} */
  public void createUnitGroup(VerifiedTenantContext c,UnitGroupInput input){
    require(c,"productunit","create");
    if(input==null||input.code()==null||!input.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid("PRODUCT_CATALOG_INVALID");
    String name=groupName(input);
    integrity(()->tx.required(()->{
      authorizeWrite(c,"masterdata.productunit:create",input.code());
      statements.createUnitGroup(tenant(c),input.code(),name,actor(c));
      return true;
    }));
  }

  /** {@inheritDoc} */
  public void renameUnitGroup(VerifiedTenantContext c,String code,UnitGroupInput input){
    require(c,"productunit","update");
    String name=groupName(input);
    if(code==null||code.length()>64||input.version()==null||input.version()<0)throw invalid("PRODUCT_CATALOG_INVALID");
    tx.required(()->{
      if(code.isBlank())throw changed();
      authorizeWrite(c,"masterdata.productunit:update",code);
      changed(statements.renameUnitGroup(tenant(c),code,name,actor(c),input.version()));
      return true;
    });
  }

  private static String groupName(UnitGroupInput input){
    if(input==null||input.name()==null||input.name().isBlank()||input.name().trim().length()>200)throw invalid("PRODUCT_CATALOG_INVALID");
    return input.name().trim();
  }

  /** 创建单位；同租户编码唯一由数据库兜底，创建与审计同事务。 */
  public Unit createUnit(VerifiedTenantContext c,UnitInput input){
    require(c,"productunit","create");unitInput(input);String id=UUID.randomUUID().toString();
    return integrity(()->tx.required(()->{
      authorizeWrite(c,"masterdata.productunit:create",input.dimensionCode().trim());
      statements.ensureUnitGroup(tenant(c),input.dimensionCode().trim(),actor(c));
      authorizeWrite(c,"masterdata.productunit:create",id);
      statements.createUnit(id,tenant(c),input.code().trim(),input.name().trim(),input.dimensionCode().trim(),input.precisionScale(),actor(c),actor(c));
      return readUnit(c,id);
    }));
  }

  /**
   * 名称修改不影响维度/精度；后两者首版即不可修改，避免后续引用加入时历史含义漂移。
   * @param c 可信上下文 @param id 单位UUID @param input 编码/维度/精度必须保持原值，version为当前值
   * @return 修改后的已授权单位；CAS失败409，创建审计不变
   */
  public Unit updateUnit(VerifiedTenantContext c,String id,UnitInput input){
    require(c,"productunit","update");uuid(id);unitInput(input);
    return tx.required(()->{
      authorizeWrite(c,"masterdata.productunit:update",id);
      int count=statements.renameUnit(input.name().trim(),actor(c),tenant(c),id,input.code().trim(),input.dimensionCode().trim(),input.precisionScale(),input.version());
      changed(count);return readUnit(c,id);
    });
  }

  /** 单位独立启停；后续档案引用接入后，在启用商品时仍需重验单位状态。 */
  public Unit statusUnit(VerifiedTenantContext c,String id,Status input){
    require(c,"productunit","set_status");uuid(id);statusInput(input);
    return tx.required(()->{
      authorizeWrite(c,"masterdata.productunit:set_status",id);
      changed(statements.setUnitStatus(input.enabled(),actor(c),tenant(c),id,input.version()));
      return readUnit(c,id);
    });
  }

  /**
   * 当前层分类分页；keyword不跨层，根parentId为空。树移动后查询读当前层，历史路径不重写。
   * @param c 可信上下文 @param parentId 同租户父UUID，空为根
   * @param keyword 字面编码/名称关键词，最大200字符 @param page 1起页码 @param size 1至100
   * @return 合法分类树当前层与真实总数
   */
  public CategoryPage categories(VerifiedTenantContext c,String parentId,String keyword,int page,int size){
    require(c,"productcategory","read");page(keyword,page,size);if(parentId!=null)uuid(parentId);
    var all=readTree(c);if(parentId!=null&&!all.containsKey(parentId))throw changed();
    var matches=all.values().stream().filter(x->Objects.equals(x.parentId(),parentId))
      .filter(x->x.code().contains(keyword.trim())||x.name().contains(keyword.trim())).sorted(Comparator.comparing(Category::code).thenComparing(Category::id)).toList();
    return new CategoryPage(matches.stream().skip((long)(page-1)*size).limit(size).toList(),matches.size());
  }

  /** 创建分类，串行树校验包括父级启用状态、最多8层；根无隐式模板。 */
  public Category createCategory(VerifiedTenantContext c,CategoryInput input){
    require(c,"productcategory","create");categoryInput(input);String id=UUID.randomUUID().toString();
    return integrity(()->tx.required(()->{
      lockTree(c);var all=readTree(c);validateParent(all,input.parentId());depthFor(all,input.parentId());
      authorizeWrite(c,"masterdata.productcategory:create",id);
      statements.createCategory(id,tenant(c),input.code().trim(),input.name().trim(),input.parentId(),actor(c),actor(c));
      return readTree(c).get(id);
    }));
  }

  /** 分类修改/移动使用当前聚合版本；整棵子树最大深度及环检查在同一锁内。 */
  public Category updateCategory(VerifiedTenantContext c,String id,CategoryInput input){
    require(c,"productcategory","update");uuid(id);categoryInput(input);
    return integrity(()->tx.required(()->{
      lockTree(c);var all=readTree(c);var old=all.get(id);
      if(old==null||old.version()!=input.version()||!old.code().equals(input.code().trim()))throw changed();
      validateParent(all,input.parentId());
      var proposed=new LinkedHashMap<>(all);
      proposed.put(id,new Category(id,old.code(),input.name().trim(),input.parentId(),0,old.enabled(),old.currentConfigId(),old.version(),old.audit()));
      for(var row:proposed.values())depth(proposed,row.id(),new HashSet<>());
      authorizeWrite(c,"masterdata.productcategory:update",id);
      changed(statements.moveCategory(input.name().trim(),input.parentId(),actor(c),tenant(c),id,input.version()));
      return readTree(c).get(id);
    }));
  }

  /** 分类启停不级联；停用前检查任意深度启用后代，启用前要求祖先启用。 */
  public Category statusCategory(VerifiedTenantContext c,String id,Status input){
    require(c,"productcategory","set_status");uuid(id);statusInput(input);
    return tx.required(()->{
      lockTree(c);var all=readTree(c);var current=all.get(id);
      if(current==null||current.version()!=input.version())throw changed();
      if(input.enabled())validateParent(all,current.parentId());
      else for(var row:all.values())if(row.enabled()&&!row.id().equals(id)&&isDescendant(all,row,id))throw invalid("PRODUCT_CATEGORY_HAS_ENABLED_CHILD");
      authorizeWrite(c,"masterdata.productcategory:set_status",id);
      changed(statements.setCategoryStatus(input.enabled(),actor(c),tenant(c),id,input.version()));
      return readTree(c).get(id);
    });
  }

  /**
   * 发布四槽完整配置，固定已发布版本并检查用途及稳定属性重复。
   * @param c 可信上下文 @param id 分类UUID @param input 四个既有槽必传，SKU及BATCH是下游默认模板的可选槽，version为类目CAS
   * @return 固定发布记录；任何校验失败连同新版本与绑定全部回滚
   */
  public Configuration publish(VerifiedTenantContext c,String id,Publish input){
    require(c,"productcategory","publish");uuid(id);
    if(input==null||input.templates()==null||input.serviceUnitRequired()==null||input.version()<0
        ||!input.templates().keySet().containsAll(PRODUCT_SLOTS)
        ||!TEMPLATE_SLOTS.containsAll(input.templates().keySet())){
      throw invalid("PRODUCT_CONFIG_SLOTS_INVALID");
    }
    return integrity(()->tx.required(()->{
      lockTree(c);var all=readTree(c);var category=all.get(id);
      if(category==null||category.version()!=input.version())throw changed();
      if(!category.enabled())throw invalid("PRODUCT_CATEGORY_DISABLED");
      var selected=new LinkedHashMap<String,String>();var attributes=new HashSet<String>();
      for(String purpose:TEMPLATE_SLOTS){
        String versionId=input.templates().get(purpose);if(versionId==null)continue;uuid(versionId);
        var versions=statements.publishedTemplate(tenant(c),versionId);
        if(versions.size()!=1||!"PUBLISHED".equals(versions.getFirst().get("state"))||!purpose.equals(versions.getFirst().get("purpose"))||!Boolean.TRUE.equals(versions.getFirst().get("enabled")))throw invalid("PRODUCT_TEMPLATE_NOT_PUBLISHED");
        var items=statements.templateAttributes(tenant(c),versionId);
        if(items.isEmpty())throw invalid("PRODUCT_TEMPLATE_EMPTY");
        if(Set.of("BASIC","QUALITY","EXTENSION").contains(purpose)){
          for(String attribute:items)if(!attributes.add(attribute))throw invalid("PRODUCT_CONFIG_ATTRIBUTE_DUPLICATED");
        }
        selected.put(purpose,versionId);
      }
      long revision=statements.nextConfigurationRevision(tenant(c),id);
      String configId=UUID.randomUUID().toString();
      writes.authorizeCatalogPublication(access,AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.productcategory:publish");
      statements.createConfiguration(configId,tenant(c),id,revision,path(all,id),input.serviceUnitRequired(),actor(c),actor(c));
      for(var entry:selected.entrySet())statements.bindTemplate(UUID.randomUUID().toString(),tenant(c),configId,entry.getKey(),entry.getValue(),actor(c),actor(c));
      statements.freezeConfigurationItem(actor(c),actor(c),tenant(c),configId);
      changed(statements.activateConfiguration(configId,actor(c),tenant(c),id,input.version()));
      return configRow(c,id,configId);
    }));
  }

  /** 只读固定配置，不用当前指针重新解释历史；跨分类/跨租户ID与不存在统一409。 */
  public Configuration configuration(VerifiedTenantContext c,String categoryId,String configId){
    require(c,"productcategory","read");uuid(categoryId);uuid(configId);
    return configRow(c,categoryId,configId);
  }

  private Configuration configRow(VerifiedTenantContext c,String categoryId,String configId){
    var rows=statements.configuration(tenant(c),categoryId,configId).stream().map(row->new Configuration((String)row.get("id"),categoryId,((Number)row.get("revision_no")).longValue(),(String)row.get("display_path_snapshot"),Boolean.TRUE.equals(row.get("service_unit_required")),slots(c,configId),audit(row))).toList();
    if(rows.isEmpty())throw changed();return rows.getFirst();
  }

  /** 事务锚点不借用其他领域表；INSERT IGNORE仅建首次锁行，不更新其创建责任。 */
  private void lockTree(VerifiedTenantContext c){
    statements.ensureCategoryGuard(tenant(c),actor(c),actor(c));
    statements.lockCategoryGuard(tenant(c));
  }
  private Map<String,Category> readTree(VerifiedTenantContext c){
    var rows=statements.tree(tenant(c)).stream().map(row->new Category((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),(String)row.get("parent"),0,Boolean.TRUE.equals(row.get("enabled")),(String)row.get("config"),((Number)row.get("version")).longValue(),audit(row))).toList();
    var all=new LinkedHashMap<String,Category>();for(var row:rows)all.put(row.id(),row);
    var result=new LinkedHashMap<String,Category>();
    for(var row:rows)result.put(row.id(),new Category(row.id(),row.code(),row.name(),row.parentId(),depth(all,row.id(),new HashSet<>()),row.enabled(),row.currentConfigId(),row.version(),row.audit()));
    return result;
  }
  private static int depth(Map<String,Category> all,String id,Set<String> visited){
    if(id==null)return 0;if(!visited.add(id))throw invalid("PRODUCT_CATEGORY_CYCLE");
    var row=all.get(id);if(row==null)throw changed();int value=1+depth(all,row.parentId(),visited);
    if(value>8)throw invalid("PRODUCT_CATEGORY_DEPTH");return value;
  }
  private static void depthFor(Map<String,Category> all,String parent){if(1+depth(all,parent,new HashSet<>())>8)throw invalid("PRODUCT_CATEGORY_DEPTH");}
  private static void validateParent(Map<String,Category> all,String parent){
    var visited=new HashSet<String>();while(parent!=null){
      if(!visited.add(parent))throw invalid("PRODUCT_CATEGORY_CYCLE");var row=all.get(parent);
      if(row==null||!row.enabled())throw invalid("PRODUCT_CATEGORY_PARENT_INVALID");parent=row.parentId();
    }
  }
  private static boolean isDescendant(Map<String,Category> all,Category row,String ancestor){
    String parent=row.parentId();while(parent!=null){if(parent.equals(ancestor))return true;parent=all.get(parent).parentId();}return false;
  }
  private static String path(Map<String,Category> all,String id){
    var parts=new LinkedList<String>();while(id!=null){var row=all.get(id);parts.addFirst(row.name());id=row.parentId();}return String.join(" / ",parts);
  }
  private Unit readUnit(VerifiedTenantContext c,String id){
    var rows=statements.unit(tenant(c),id).stream().map(ProductCatalogService::unit).toList();
    if(rows.isEmpty())throw changed();return rows.getFirst();
  }
  private static Unit unit(Map<String,Object> row){return new Unit((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),(String)row.get("dimension_code"),((Number)row.get("precision_scale")).intValue(),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("version")).longValue(),audit(row));}
  private static MasterData.Audit audit(Map<String,Object> row){return new MasterData.Audit((String)row.get("creator"),utc(row.get("created_at")),(String)row.get("updater"),utc(row.get("updated_at")));}
  private static String utc(Object value){return value==null?null:((java.time.Instant)value).toString();}
  private Map<String,String> slots(VerifiedTenantContext c,String configId){
    var slots=emptySlots();
    for(var row:statements.slots(tenant(c),configId))slots.put((String)row.get("purpose"),(String)row.get("version_id"));
    return slots;
  }
  private static Map<String,String> emptySlots(){var result=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))result.put(purpose,null);return result;}
  /** 目录没有组织行策略，事务许可按原操作权限和确切记录键签发。 */
  private void authorizeWrite(VerifiedTenantContext c,String permission,String id){
    var subject=AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());
    try{
      writes.authorizeServiceOperation(c.tenantId(),permission,List.of(id),()->access.hasPermission(subject,permission));
    }catch(SecurityException denied){
      throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");
    }
  }
  private void require(VerifiedTenantContext c,String resource,String action){
    if(c==null||c.accountId()==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata."+resource+":"+action))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");
  }
  private static void unitInput(UnitInput input){
    if(input==null)throw invalid("PRODUCT_INPUT_INVALID");common(input.code(),input.name(),input.version());
    if(input.dimensionCode()==null||!input.dimensionCode().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||input.precisionScale()<0||input.precisionScale()>12)throw invalid("PRODUCT_UNIT_INVALID");
  }
  private static void categoryInput(CategoryInput input){
    if(input==null)throw invalid("PRODUCT_INPUT_INVALID");common(input.code(),input.name(),input.version());if(input.parentId()!=null)uuid(input.parentId());
  }
  private static void common(String code,String name,long version){
    if(code==null||!code.trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||name==null||name.isBlank()||name.trim().length()>200||name.chars().anyMatch(Character::isISOControl)||version<0)throw invalid("PRODUCT_INPUT_INVALID");
  }
  private static void statusInput(Status input){if(input==null||input.enabled()==null||input.version()<0)throw invalid("PRODUCT_INPUT_INVALID");}
  private static void page(String keyword,int page,int size){if(keyword==null||keyword.length()>200||page<1||page>1000000||size<1||size>100)throw invalid("PRODUCT_INPUT_INVALID");}
  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid("PRODUCT_INPUT_INVALID");}catch(IllegalArgumentException|NullPointerException e){throw invalid("PRODUCT_INPUT_INVALID");}}
  private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
  private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
  private static MasterData.Rejection invalid(String code){return new MasterData.Rejection(400,code);}
  private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"MASTER_DATA_CHANGED");}
  private static void changed(int count){if(count!=1)throw changed();}
  private <T>T integrity(java.util.function.Supplier<T> operation){return tx.integrity(operation);}
}
