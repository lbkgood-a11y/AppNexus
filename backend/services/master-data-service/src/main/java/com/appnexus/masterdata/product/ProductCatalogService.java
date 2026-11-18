package com.appnexus.masterdata.product;

import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.appnexus.masterdata.product.CatalogData.*;

/**
 * 商品单位、分类和不可变空配置的领域执行器。
 * 所有SQL值参数绑定，租户/账号仅来自可信上下文；树写入使用租户锚点行锁直到提交，
 * 防止两个事务分别校验通过却互相移入。分类移动不修改任何历史发布路径或配置。
 */
@Service
public final class ProductCatalogService {
  private final JdbcTemplate jdbc;
  private final AccessAuthorization access;
  private final TransactionTemplate tx;
  private static final Set<String> PURPOSES=Set.of("BASIC","SPEC","QUALITY","EXTENSION");
  private static final String AUDIT_SELECT="BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at";

  /** @param jdbc 宿主JDBC，连接时区须为UTC @param access 公开授权端口 @param manager 宿主事务管理器 */
  public ProductCatalogService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){
    this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);
  }

  /** 单位分页结果；总数使用与行完全相同的租户/筛选条件。 */
  public record UnitPage(List<Unit> rows,long total) {}
  /** 分类分页结果；先验证全树合法，再返回指定父节点的当前层。 */
  public record CategoryPage(List<Category> rows,long total) {}

  /** @param c 可信上下文 @param keyword 字面关键词，最大200字符 @param page 1起页码 @param size 1至100条 @return 真实单位分页 */
  public UnitPage units(VerifiedTenantContext c,String keyword,int page,int size){
    require(c,"productunit","read");page(keyword,page,size);
    var args=new Object[]{tenant(c),keyword.trim(),keyword.trim()};
    String where=" WHERE tenant_id=UUID_TO_BIN(?) AND (LOCATE(?,code)>0 OR LOCATE(?,name)>0)";
    long total=jdbc.queryForObject("SELECT COUNT(*) FROM md_product_unit"+where,Long.class,args);
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,dimension_code,precision_scale,enabled,version,"+AUDIT_SELECT+" FROM md_product_unit"+where+" ORDER BY code,id LIMIT ? OFFSET ?",(r,n)->unit(r),tenant(c),keyword.trim(),keyword.trim(),size,(page-1)*size);
    return new UnitPage(rows,total);
  }

  /** 创建单位；同租户编码唯一由数据库兜底，创建与审计同事务。 */
  public Unit createUnit(VerifiedTenantContext c,UnitInput input){
    require(c,"productunit","create");unitInput(input);String id=UUID.randomUUID().toString();
    return integrity(()->tx.execute(status->{
      jdbc.update("INSERT INTO md_product_unit(id,tenant_id,code,name,dimension_code,precision_scale,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",
        id,tenant(c),input.code().trim(),input.name().trim(),input.dimensionCode().trim(),input.precisionScale(),actor(c),actor(c));
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
    return tx.execute(status->{
      int count=jdbc.update("UPDATE md_product_unit SET name=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND code=? AND dimension_code=? AND precision_scale=? AND version=?",
        input.name().trim(),actor(c),tenant(c),id,input.code().trim(),input.dimensionCode().trim(),input.precisionScale(),input.version());
      changed(count);return readUnit(c,id);
    });
  }

  /** 单位独立启停；后续档案引用接入后，在启用商品时仍需重验单位状态。 */
  public Unit statusUnit(VerifiedTenantContext c,String id,Status input){
    require(c,"productunit","set_status");uuid(id);statusInput(input);
    return tx.execute(status->{
      changed(jdbc.update("UPDATE md_product_unit SET enabled=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",input.enabled(),actor(c),tenant(c),id,input.version()));
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
    return integrity(()->tx.execute(status->{
      lockTree(c);var all=readTree(c);validateParent(all,input.parentId());depthFor(all,input.parentId());
      jdbc.update("INSERT INTO md_product_category(id,tenant_id,code,name,parent_id,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
        id,tenant(c),input.code().trim(),input.name().trim(),input.parentId(),actor(c),actor(c));
      return readTree(c).get(id);
    }));
  }

  /** 分类修改/移动使用当前聚合版本；整棵子树最大深度及环检查在同一锁内。 */
  public Category updateCategory(VerifiedTenantContext c,String id,CategoryInput input){
    require(c,"productcategory","update");uuid(id);categoryInput(input);
    return integrity(()->tx.execute(status->{
      lockTree(c);var all=readTree(c);var old=all.get(id);
      if(old==null||old.version()!=input.version()||!old.code().equals(input.code().trim()))throw changed();
      validateParent(all,input.parentId());
      var proposed=new LinkedHashMap<>(all);
      proposed.put(id,new Category(id,old.code(),input.name().trim(),input.parentId(),0,old.enabled(),old.currentConfigId(),old.version(),old.audit()));
      for(var row:proposed.values())depth(proposed,row.id(),new HashSet<>());
      changed(jdbc.update("UPDATE md_product_category SET name=?,parent_id=UUID_TO_BIN(?),version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",
        input.name().trim(),input.parentId(),actor(c),tenant(c),id,input.version()));
      return readTree(c).get(id);
    }));
  }

  /** 分类启停不级联；停用前检查任意深度启用后代，启用前要求祖先启用。 */
  public Category statusCategory(VerifiedTenantContext c,String id,Status input){
    require(c,"productcategory","set_status");uuid(id);statusInput(input);
    return tx.execute(status->{
      lockTree(c);var all=readTree(c);var current=all.get(id);
      if(current==null||current.version()!=input.version())throw changed();
      if(input.enabled())validateParent(all,current.parentId());
      else for(var row:all.values())if(row.enabled()&&!row.id().equals(id)&&isDescendant(all,row,id))throw invalid("PRODUCT_CATEGORY_HAS_ENABLED_CHILD");
      changed(jdbc.update("UPDATE md_product_category SET enabled=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",
        input.enabled(),actor(c),tenant(c),id,input.version()));
      return readTree(c).get(id);
    });
  }

  /**
   * 发布四槽完整配置，固定已发布版本并检查用途及稳定属性重复。
   * @param c 可信上下文 @param id 分类UUID @param input 四槽齐全，SPEC必须NULL，version为分类CAS
   * @return 固定发布记录；任何校验失败连同新版本与绑定全部回滚
   */
  public Configuration publish(VerifiedTenantContext c,String id,Publish input){
    require(c,"productcategory","publish");uuid(id);
    if(input==null||input.templates()==null||!input.templates().keySet().equals(PURPOSES)||input.serviceUnitRequired()==null||input.version()<0)throw invalid("PRODUCT_CONFIG_SLOTS_INVALID");
    return integrity(()->tx.execute(status->{
      lockTree(c);var all=readTree(c);var category=all.get(id);
      if(category==null||category.version()!=input.version())throw changed();
      if(!category.enabled())throw invalid("PRODUCT_CATEGORY_DISABLED");
      var selected=new LinkedHashMap<String,String>();var attributes=new HashSet<String>();
      for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION")){
        String versionId=input.templates().get(purpose);if(versionId==null)continue;uuid(versionId);
        var versions=jdbc.queryForList("SELECT tv.state,t.purpose,t.enabled FROM md_product_template_version tv JOIN md_product_template t ON t.tenant_id=tv.tenant_id AND t.id=tv.template_id WHERE tv.tenant_id=UUID_TO_BIN(?) AND tv.id=UUID_TO_BIN(?) FOR UPDATE",tenant(c),versionId);
        if(versions.size()!=1||!"PUBLISHED".equals(versions.getFirst().get("state"))||!purpose.equals(versions.getFirst().get("purpose"))||!Boolean.TRUE.equals(versions.getFirst().get("enabled")))throw invalid("PRODUCT_TEMPLATE_NOT_PUBLISHED");
        var items=jdbc.queryForList("SELECT BIN_TO_UUID(attribute_id) id FROM md_product_template_item WHERE tenant_id=UUID_TO_BIN(?) AND template_version_id=UUID_TO_BIN(?)",String.class,tenant(c),versionId);
        if(items.isEmpty())throw invalid("PRODUCT_TEMPLATE_EMPTY");
        for(String attribute:items)if(!attributes.add(attribute))throw invalid("PRODUCT_CONFIG_ATTRIBUTE_DUPLICATED");
        selected.put(purpose,versionId);
      }
      long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?)",Long.class,tenant(c),id);
      String configId=UUID.randomUUID().toString();
      jdbc.update("INSERT INTO md_product_category_config(id,tenant_id,category_id,revision_no,display_path_snapshot,service_unit_required,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",configId,tenant(c),id,revision,path(all,id),input.serviceUnitRequired(),actor(c),actor(c));
      for(var entry:selected.entrySet())jdbc.update("INSERT INTO md_product_category_template(id,tenant_id,config_id,purpose,template_version_id,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant(c),configId,entry.getKey(),entry.getValue(),actor(c),actor(c));
      jdbc.update("INSERT INTO md_product_category_config_item(id,tenant_id,config_id,purpose,template_version_id,template_item_id,attribute_id,attribute_revision_id,data_type,created_by,updated_by) SELECT UUID_TO_BIN(UUID()),b.tenant_id,b.config_id,b.purpose,b.template_version_id,i.id,i.attribute_id,i.attribute_revision_id,i.data_type,UUID_TO_BIN(?),UUID_TO_BIN(?) FROM md_product_category_template b JOIN md_product_template_item i ON i.tenant_id=b.tenant_id AND i.template_version_id=b.template_version_id WHERE b.tenant_id=UUID_TO_BIN(?) AND b.config_id=UUID_TO_BIN(?)",actor(c),actor(c),tenant(c),configId);
      changed(jdbc.update("UPDATE md_product_category SET current_config_id=UUID_TO_BIN(?),version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",configId,actor(c),tenant(c),id,input.version()));
      return configRow(c,id,configId);
    }));
  }

  /** 只读固定配置，不用当前指针重新解释历史；跨分类/跨租户ID与不存在统一409。 */
  public Configuration configuration(VerifiedTenantContext c,String categoryId,String configId){
    require(c,"productcategory","read");uuid(categoryId);uuid(configId);
    return configRow(c,categoryId,configId);
  }

  private Configuration configRow(VerifiedTenantContext c,String categoryId,String configId){
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,revision_no,display_path_snapshot,service_unit_required,"+AUDIT_SELECT+" FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
      (r,n)->new Configuration(r.getString("id"),categoryId,r.getLong("revision_no"),r.getString("display_path_snapshot"),r.getBoolean("service_unit_required"),slots(c,configId),audit(r)),tenant(c),categoryId,configId);
    if(rows.isEmpty())throw changed();return rows.getFirst();
  }

  /** 事务锚点不借用其他领域表；INSERT IGNORE仅建首次锁行，不更新其创建责任。 */
  private void lockTree(VerifiedTenantContext c){
    jdbc.update("INSERT IGNORE INTO md_product_category_guard(tenant_id,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",tenant(c),actor(c),actor(c));
    jdbc.queryForObject("SELECT BIN_TO_UUID(tenant_id) FROM md_product_category_guard WHERE tenant_id=UUID_TO_BIN(?) FOR UPDATE",String.class,tenant(c));
  }
  private Map<String,Category> readTree(VerifiedTenantContext c){
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,BIN_TO_UUID(parent_id) parent,enabled,BIN_TO_UUID(current_config_id) config,version,"+AUDIT_SELECT+" FROM md_product_category WHERE tenant_id=UUID_TO_BIN(?)",
      (r,n)->new Category(r.getString("id"),r.getString("code"),r.getString("name"),r.getString("parent"),0,r.getBoolean("enabled"),r.getString("config"),r.getLong("version"),audit(r)),tenant(c));
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
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,dimension_code,precision_scale,enabled,version,"+AUDIT_SELECT+" FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",(r,n)->unit(r),tenant(c),id);
    if(rows.isEmpty())throw changed();return rows.getFirst();
  }
  private static Unit unit(ResultSet r)throws SQLException{return new Unit(r.getString("id"),r.getString("code"),r.getString("name"),r.getString("dimension_code"),r.getInt("precision_scale"),r.getBoolean("enabled"),r.getLong("version"),audit(r));}
  private static MasterData.Audit audit(ResultSet r)throws SQLException{
    return new MasterData.Audit(r.getString("creator"),r.getTimestamp("created_at")==null?null:r.getTimestamp("created_at").toInstant().toString(),r.getString("updater"),r.getTimestamp("updated_at")==null?null:r.getTimestamp("updated_at").toInstant().toString());
  }
  private Map<String,String> slots(VerifiedTenantContext c,String configId){
    var slots=emptySlots();
    jdbc.query("SELECT purpose,BIN_TO_UUID(template_version_id) version_id FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(?) AND config_id=UUID_TO_BIN(?)",rs->{slots.put(rs.getString("purpose"),rs.getString("version_id"));},tenant(c),configId);
    return slots;
  }
  private static Map<String,String> emptySlots(){var result=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))result.put(purpose,null);return result;}
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
  private static <T>T integrity(java.util.function.Supplier<T> operation){
    try{return operation.get();}catch(DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_CATALOG_CONFLICT");}
  }
}
