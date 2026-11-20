from pathlib import Path
import json,re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product')
ops=json.loads(Path('.vite/product-operations.json').read_text(encoding='utf-8'))
ops += [dict(code='createOrganizations',id='Product.createOrganizations',zh='查询新增商品归属组织',en='Read create product organizations',permission='masterdata.product:create',method='GET',path='/api/master-data/product/organizations/create',pages=['master-data/product']),dict(code='updateOrganizations',id='Product.updateOrganizations',zh='查询修改商品归属组织',en='Read update product organizations',permission='masterdata.product:update',method='GET',path='/api/master-data/product/organizations/update',pages=['master-data/product'])]
quote=lambda s:json.dumps(s,ensure_ascii=False)
declarations=',\n'.join('      operation('+','.join(quote(o[k]) for k in ['code','zh','en','permission','method','path'])+',List.of('+','.join(map(quote,o['pages']))+'))' for o in ops)
provider='''package com.appnexus.masterdata.product;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Component;
/** 商品唯一声明；主档、规格SKU、图片与历史继承父商品组织范围，不授予未声明的RULES或字段能力。 */
@Component
public final class ProductDeclaration implements BusinessObjects.Provider {
 @Override public Descriptor descriptor(){
  var operations=List.of(
'''+declarations+''');
  var scopes=new TreeMap<String,Set<String>>();
  operations.forEach(op->scopes.put(op.operationCode(),Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE")));
  return new Descriptor("Product","商品档案","Products","master-data",2,Category.MASTER_DATA,List.of(),operations,
   new DataAccess("TENANT",List.of(new DataResource("masterdata.product","","商品档案","Products",List.of())),scopes));
 }
 /** 固定页面和真实API来自代码；预览等额外读取前置仍由原用例复核，不以声明替代执行。 */
 private static Operation operation(String code,String zh,String en,String permission,String method,String path,List<String> pages){
  String id="Product."+code;
  var views=pages.stream().map(key->{var page=Objects.requireNonNull(NavigationPages.find(key));return new PageOperation(key,page.path(),page.titleZh(),page.titleEn(),method,path);}).toList();
  var interaction=new InteractionContract(id,1,"Product",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),"product."+code,1)));
  return new Operation(code,zh,en,permission,id,views.getFirst(),views.stream().skip(1).map(v->new OperationAlias(id,v)).toList(),interaction);
 }
}
'''
(root/'ProductDeclaration.java').write_text(provider,encoding='utf-8')
for name in ['ProductController.java','ProductStructureController.java','ProductImageController.java']:
 p=root/name;s=p.read_text(encoding='utf-8')
 s=re.sub(r'@OperationBinding\(([^)]*)\)',lambda m:'@OperationBinding(id="'+re.search('id="([^"]+)"',m.group(1)).group(1)+'",reference=true)' if 'objectType="Product"' in m.group(1) else m.group(0),s)
 s=re.sub(r'@BusinessObjectType\([^\n]+\)\n','',s)
 p.write_text(s,encoding='utf-8')
p=root/'ProductController.java';s=p.read_text(encoding='utf-8');s=s.replace(' private final ProductV1Service service;',''' private final ProductV1Service service;
 /** 新增与修改各自独立准入，不要求获得商品读取或组织管理权限。 */
 @GetMapping("/organizations/create") @OperationBinding(id="Product.createOrganizations",reference=true)
 public Object createOrganizations(HttpServletRequest request){return service.organizations(ctx(request),"create");}
 /** 修改归属候选与保存时使用同一操作范围，不能默认选择首项。 */
 @GetMapping("/organizations/update") @OperationBinding(id="Product.updateOrganizations",reference=true)
 public Object updateOrganizations(HttpServletRequest request){return service.organizations(ctx(request),"update");}''');p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc/ProductAccess.java');s=p.read_text(encoding='utf-8').replace('public final class ProductAccess','@org.springframework.stereotype.Component\npublic final class ProductAccess');p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/api/product/ProductMainData.java');s=p.read_text(encoding='utf-8')
s=s.replace('List<Value> values,String barcode){','List<Value> values,String barcode,String organizationId){',1)
s=s.replace('this(code,name,shortName,categoryId,configId,productForm,unitId,description,brand,origin,values,"");','this(code,name,shortName,categoryId,configId,productForm,unitId,description,brand,origin,values,"",null);')
pos=s.index('  /** 内部原调用')
s=s[:pos]+'''  /** 原内部调用须显式补归属后方可保存；此构造仅保留源码兼容，不赋予默认组织。 */
  public Input(String code,String name,String shortName,String categoryId,String configId,String productForm,String unitId,String description,String brand,String origin,List<Value> values,String barcode){this(code,name,shortName,categoryId,configId,productForm,unitId,description,brand,origin,values,barcode,null);}
'''+s[pos:]
s=s.replace('Long version,String barcode){','Long version,String barcode,String organizationId){').replace('this(name,shortName,description,brand,origin,values,version,null);','this(name,shortName,description,brand,origin,values,version,null,null);')
pos=s.index('  /** 原内部调用不修改条码')
s=s[:pos]+'''  /** 缺省归属保留原值，不允许传空串清除。 */
  public Update(String name,String shortName,String description,String brand,String origin,List<Value> values,Long version,String barcode){this(name,shortName,description,brand,origin,values,version,barcode,null);}
'''+s[pos:]
s=s.replace('* @param barcode 条码文本','* @param organizationId 新增必填的本租户启用归属组织UUID，必须处于create范围\n  * @param barcode 条码文本').replace('* @param barcode 条码最多','* @param organizationId 修改目标组织UUID；null保留，空串拒绝，原记录与目标分别校验update范围\n  * @param barcode 条码最多')
p.write_text(s,encoding='utf-8')
# 商品正文之前授权，写入在原事务加父行锁；内部快照不额外借read权限替代当前操作。
p=root/'ProductV1Service.java';s=p.read_text(encoding='utf-8')
s=s.replace('private final JdbcTemplate jdbc;','private final com.appnexus.masterdata.adapter.jdbc.ProductAccess productAccess;\n private final JdbcTemplate jdbc;')
s=s.replace('com.appnexus.masterdata.adapter.jdbc.ProductCatalogReadMapper catalogMapper){this.catalogMapper=','com.appnexus.masterdata.adapter.jdbc.ProductCatalogReadMapper catalogMapper,com.appnexus.masterdata.adapter.jdbc.ProductAccess productAccess){this.productAccess=productAccess;this.catalogMapper=')
s=s.replace('* @param catalogMapper 已授权商品操作的引用目录与锁定预检映射','* @param catalogMapper 已授权商品操作的引用目录与锁定预检映射\n  * @param productAccess 统一商品范围执行器，所有正文入口必须实际接通')
s=s.replace('new com.appnexus.masterdata.adapter.jdbc.MyBatisProductList(listMapper,json)','new com.appnexus.masterdata.adapter.jdbc.MyBatisProductList(listMapper,json,productAccess)')
s=s.replace('require(c,"read");uuid(id);return snapshot(c,id);','require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);return snapshot(c,id);')
s=s.replace('@Override public void references(VerifiedTenantContext c,Input input){validateReferences','@Override public void references(VerifiedTenantContext c,Input input){productAccess.target(c,input.organizationId(),"create",true);validateReferences')
s=s.replace('barcode,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))','barcode,organization_id,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))',1)
s=s.replace('Objects.toString(input.barcode(),""),actor(c),actor(c)','Objects.toString(input.barcode(),""),input.organizationId(),actor(c),actor(c)',1)
s=s.replace('before=locked(c,id,version);return before;','authorizeRecord(c,id,"update",true);before=locked(c,id,version);return before;',1)
s=s.replace('@Override public void updateFields(VerifiedTenantContext c,String id,Update input){','@Override public void updateFields(VerifiedTenantContext c,String id,Update input){productAccess.target(c,input.organizationId(),"update",false);')
s=s.replace('barcode=COALESCE(?,barcode) WHERE tenant_id','barcode=COALESCE(?,barcode),organization_id=COALESCE(UUID_TO_BIN(?),organization_id) WHERE tenant_id',1).replace('input.origin(),input.barcode(),tenant(c),id','input.origin(),input.barcode(),input.organizationId(),tenant(c),id',1)
s=s.replace('return write(()->service.status(c,id,input.version(),input.status()));','return write(()->{authorizeRecord(c,id,"set_status",true);return service.status(c,id,input.version(),input.status());});')
s=s.replace('return write(()->service.archive(c,id,input.version(),archived));','return write(()->{authorizeRecord(c,id,"manage_draft",true);return service.archive(c,id,input.version(),archived);});')
s=s.replace('require(c,"read");uuid(id);if(page<1','require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);if(page<1')
s=s.replace('require(c,"migrate_structure");require(c,"read");uuid(id);','require(c,"migrate_structure");require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);authorizeRecord(c,id,"migrate_structure",false);')
s=s.replace('row(c,id,true);ensureNotInApproval(c,id);','authorizeRecord(c,id,"migrate_structure",true);row(c,id,true);ensureNotInApproval(c,id);')
s=s.replace('require(c,"stage_legacy");require(c,"read");','require(c,"stage_legacy");require(c,"read");productAccess.requireAll(c,"stage_legacy");')
s=s.replace('private static MasterData.Rejection', 'private static MasterData.Rejection',1)
insertion='''
 /** 所有商品扩展读取正文前使用当前真实操作，写用例在原事务锁定授权父行。 */
 void authorizeRecord(VerifiedTenantContext c,String id,String action,boolean lock){productAccess.authorize(c,id,action,lock);}
 /** @param c 可信主体 @param action create/update独立准入 @return 处于对应范围内的启用组织候选 */
 public Object organizations(VerifiedTenantContext c,String action){return productAccess.organizations(c,action);}
'''
s=s.rstrip()[:-1]+insertion+'}\n';p.write_text(s,encoding='utf-8')
for name,method in [('ProductImageService.java','product'),('ProductStructureService.java','parent')]:
 p=root/name;s=p.read_text(encoding='utf-8')
 marker='private Map<String,Object> '+method+'(VerifiedTenantContext c,String id,boolean lock,Long version){'
 if marker not in s:raise ValueError(name)
 s=s.replace(marker,marker+'products.authorizeRecord(c,id,lock?"'+('update' if method=='product' else 'manage_structure')+'":"read",lock);')
 p.write_text(s,encoding='utf-8')
# 归属事实进入原快照和列表；列表COUNT复用相同固定范围条件。
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc/ProductRecordMapper.java');s=p.read_text(encoding='utf-8').replace('id,code,name,short_name','id,BIN_TO_UUID(organization_id) organizationId,code,name,short_name');p.write_text(s,encoding='utf-8')
p=p.with_name('MyBatisProductRecords.java');s=p.read_text(encoding='utf-8').replace('List.of("id","code"','List.of("organizationId","id","code"');p.write_text(s,encoding='utf-8')
p=p.with_name('ProductListMapper.java');s=p.read_text(encoding='utf-8').replace('Query(String name,','Query(com.appnexus.platform.authorization.RowAccessControl.OrganizationRange range,String name,').replace('* @param name 最多200','* @param range 本次读取操作的统一组织范围，非ALL空集合拒绝\n     * @param name 最多200');p.write_text(s,encoding='utf-8')
p=p.with_name('MyBatisProductList.java');s=p.read_text(encoding='utf-8').replace('private final ProductListMapper mapper;','private final ProductAccess access;private final ProductListMapper mapper;').replace('ProductListMapper mapper,ObjectMapper json){this.mapper=','ProductListMapper mapper,ObjectMapper json,ProductAccess access){this.access=access;this.mapper=').replace('new ProductListMapper.Query(name.trim()','new ProductListMapper.Query(access.range(c,"read"),name.trim()');p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/main/resources/com/appnexus/masterdata/adapter/jdbc/ProductListMapper.xml');s=p.read_text(encoding='utf-8').replace('WHERE tenant_id=#{__verifiedTenant} AND LOCATE','''WHERE tenant_id=#{__verifiedTenant}
    <if test="!query.range.all"><choose><when test="!query.range.ids.isEmpty()"> AND organization_id IN <foreach collection="query.range.ids" item="organization" open="(" separator="," close=")">UUID_TO_BIN(#{organization})</foreach></when><otherwise> AND 1=0</otherwise></choose></if>
    AND LOCATE''').replace('id,code,name,product_form','id,BIN_TO_UUID(organization_id) organizationId,code,name,product_form');p.write_text(s,encoding='utf-8')
