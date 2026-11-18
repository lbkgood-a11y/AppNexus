package com.appnexus.masterdata.product;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 商品图片真实存储；不信任扩展名/MIME，不接受任意URL或路径。上传直接事务绑定，无临时文件清理负担。 */
@Service public final class ProductImageService {
 private final ProductV1Service products;private final JdbcTemplate jdbc;private final AccessAuthorization access;private final TransactionTemplate tx;
 /** @param jdbc UTC数据源 @param access 原商品权限公开端口 @param manager 同数据源事务 @param products 同领域父聚合快照与历史执行器 */
 public ProductImageService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager,ProductV1Service products){this.products=products;this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** 只读图片字节；必须先通过商品读取权，不能凭图片UUID绕过租户。 */
 public record Content(String mediaType,byte[] bytes){}
 /** 图片列表不返回二进制；列表审计由统一投影提供。 */
 public Object list(VerifiedTenantContext c,String product){require(c,"read");product(c,product,false,null);return jdbc.queryForList("SELECT BIN_TO_UUID(id) id,width_px width,height_px height,media_type mediaType FROM md_product_image WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY created_at,id",c.tenantId().toString(),product);}
 /** 上传原始体最大5MiB，按解码器验证维度后重编码PNG去除原始元数据，最多10张；父CAS和图片同事务。 */
 public Object upload(VerifiedTenantContext c,String product,long version,byte[] bytes){require(c,"update");if(bytes==null||bytes.length<8||bytes.length>5242880)throw invalid();byte[] canonical;int width,height;
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();var reader=readers.next();try{String format=reader.getFormatName();if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase(Locale.ROOT)))throw invalid();reader.setInput(input,true,true);width=reader.getWidth(0);height=reader.getHeight(0);if(width<1||height<1||width>4096||height>4096||(long)width*height>16000000)throw invalid();var decoded=reader.read(0);var output=new ByteArrayOutputStream();if(!ImageIO.write(decoded,"png",output))throw invalid();canonical=output.toByteArray();if(canonical.length>5242880)throw invalid();}finally{reader.dispose();}}catch(IOException e){throw invalid();}
  final int w=width,h=height;return tx.execute(s->{product(c,product,true,version);var before=products.extensionSnapshot(c,product);if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_image WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,c.tenantId().toString(),product)>=10)throw invalid();String id=UUID.randomUUID().toString();try{jdbc.update("INSERT INTO md_product_image(id,tenant_id,product_id,media_type,image_data,content_hash,width_px,height_px,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'image/png',?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,c.tenantId().toString(),product,canonical,java.security.MessageDigest.getInstance("SHA-256").digest(canonical),w,h,c.accountId().toString(),c.accountId().toString());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}bump(c,product,version);products.extensionEvent(c,product,"IMAGE_ADD",before);return Map.of("id",id,"version",version+1);});}
 /** 只提供已绑定本租户商品的图片，返回内容不含原始文件名/EXIF。 */
 public Content content(VerifiedTenantContext c,String product,String id){require(c,"read");product(c,product,false,null);uuid(id);var rows=jdbc.query("SELECT media_type,image_data FROM md_product_image WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND deleted_at IS NULL",(r,n)->new Content(r.getString(1),r.getBytes(2)),c.tenantId().toString(),product,id);if(rows.size()!=1)throw changed();return rows.getFirst();}
 /** 解绑保留四审计和内容历史，不执行物理删除；仅停用/草稿可修改图片。 */
 public Object detach(VerifiedTenantContext c,String product,String id,long version){require(c,"update");uuid(id);return tx.execute(s->{product(c,product,true,version);var before=products.extensionSnapshot(c,product);if(jdbc.update("UPDATE md_product_image SET deleted_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND deleted_at IS NULL",c.accountId().toString(),c.tenantId().toString(),product,id)!=1)throw changed();bump(c,product,version);products.extensionEvent(c,product,"IMAGE_DETACH",before);return Map.of("version",version+1);});}
 private void product(VerifiedTenantContext c,String id,boolean write,Long version){uuid(id);var rows=jdbc.queryForList("SELECT status,draft_archived_at archived,structure_state structure,version FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(write?" FOR UPDATE":""),c.tenantId().toString(),id);if(rows.size()!=1)throw changed();var row=rows.getFirst();if(write)products.ensureNotInApproval(c,id);if(write&&(version==null||version<0||((Number)row.get("version")).longValue()!=version||"ENABLED".equals(row.get("status"))||row.get("archived")!=null||"LEGACY_PENDING".equals(row.get("structure"))))throw changed();}
 private void bump(VerifiedTenantContext c,String p,long v){if(jdbc.update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",c.accountId().toString(),c.tenantId().toString(),p,v)!=1)throw changed();}
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_IMAGE_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"MASTER_DATA_CHANGED");}
}
