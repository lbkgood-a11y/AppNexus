package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductImageData.Content;

/** 商品图片真实存储；不信任扩展名/MIME，不接受任意URL或路径。上传直接事务绑定，无临时文件清理负担。 */
public final class ProductImageService implements com.appnexus.masterdata.api.product.ProductImageManagement {
 private final ProductImageRepository statements;
 private final ProductImageContent images;

 private final ProductAggregateAccess products;private final AccessAuthorization access;private final com.appnexus.masterdata.application.RecordTransaction tx;
 /** 人员或商品完整用例依赖装配；所有输出参与原业务安全与事务约束。
  * @param access 可信功能授权公开端口，每次调用重新检查
  * @param transaction 同数据源完整事务端口，父版本、图片、历史及审计共同回滚
  * @param products 同聚合父授权、冻结状态与快照协作端口
  * @param statements 固定图片仓储，强制当前租户及父商品
  * @param images 图片格式、尺寸及重编码技术端口
  */
 public ProductImageService(AccessAuthorization access,com.appnexus.masterdata.application.RecordTransaction transaction,ProductAggregateAccess products,ProductImageRepository statements,ProductImageContent images){this.images=images;this.statements=statements;this.products=products;this.access=access;this.tx=transaction;}
 
 /** 图片列表不返回二进制；列表审计由统一投影提供。 */
 public Object list(VerifiedTenantContext c,String product){require(c,"read");var predicate=product(c,product,false,null);return statements.list(c.tenantId().toString(),product,predicate);}
 /** 上传原始体最大5MiB，按解码器验证维度后重编码PNG去除原始元数据，最多10张；父CAS和图片同事务。 */
 public Object upload(VerifiedTenantContext c,String product,long version,byte[] bytes){require(c,"update");if(bytes==null||bytes.length<8||bytes.length>5242880)throw invalid();
  var image=images.canonicalize(bytes);byte[] canonical=image.bytes();int width=image.width(),height=image.height();
  final int w=width,h=height;return tx.execute(()->{var predicate=product(c,product,true,version);var before=products.extensionSnapshot(c,product);if(statements.count(c.tenantId().toString(),product,predicate)>=10)throw invalid();String id=UUID.randomUUID().toString();try{statements.insert(id,c.tenantId().toString(),product,canonical,java.security.MessageDigest.getInstance("SHA-256").digest(canonical),w,h,c.accountId().toString(),c.accountId().toString());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}bump(c,product,version);products.extensionEvent(c,product,"IMAGE_ADD",before);return Map.of("id",id,"version",version+1);},null);}
 /** 只提供已绑定本租户商品的图片，返回内容不含原始文件名/EXIF。 */
 public Content content(VerifiedTenantContext c,String product,String id){require(c,"read");var predicate=product(c,product,false,null);uuid(id);var rows=statements.content(c.tenantId().toString(),product,id,predicate).stream().map(row->new Content((String)row.get("mediaType"),(byte[])row.get("bytes"))).toList();if(rows.size()!=1)throw changed();return rows.getFirst();}
 /** 解绑保留四审计和内容历史，不执行物理删除；仅停用/草稿可修改图片。 */
 public Object detach(VerifiedTenantContext c,String product,String id,long version){require(c,"update");uuid(id);return tx.execute(()->{product(c,product,true,version);var before=products.extensionSnapshot(c,product);if(statements.detach(c.accountId().toString(),c.tenantId().toString(),product,id)!=1)throw changed();bump(c,product,version);products.extensionEvent(c,product,"IMAGE_DETACH",before);return Map.of("version",version+1);},null);}
 private com.appnexus.platform.authorization.RowAccessControl.Predicate product(VerifiedTenantContext c,String id,boolean write,Long version){String action=write?"update":"read";products.authorizeRecord(c,id,action,write);uuid(id);var predicate=products.recordPredicate(c,action);var rows=statements.parent(c.tenantId().toString(),id,write,predicate);if(rows.size()!=1)throw changed();var row=rows.getFirst();if(write)products.ensureNotInApproval(c,id);if(write&&(version==null||version<0||((Number)row.get("version")).longValue()!=version||"ENABLED".equals(row.get("status"))||row.get("archived")!=null||"LEGACY_PENDING".equals(row.get("structure"))))throw changed();return predicate;}
 private void bump(VerifiedTenantContext c,String p,long v){if(statements.advanceParent(c.accountId().toString(),c.tenantId().toString(),p,v)!=1)throw changed();}
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_IMAGE_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"MASTER_DATA_CHANGED");}
}
