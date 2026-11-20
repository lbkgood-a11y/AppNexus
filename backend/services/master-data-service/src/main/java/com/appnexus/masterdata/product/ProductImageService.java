package com.appnexus.masterdata.product;
import com.appnexus.masterdata.adapter.jdbc.ProductImageServiceSqlMapper;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 商品图片真实存储；不信任扩展名/MIME，不接受任意URL或路径。上传直接事务绑定，无临时文件清理负担。 */
@Service public final class ProductImageService {
 private final ProductImageServiceSqlMapper statements;

 private final ProductV1Service products;private final AccessAuthorization access;private final TransactionTemplate tx;
 /** @param jdbc UTC数据源 @param access 原商品权限公开端口 @param manager 同数据源事务 @param products 同领域父聚合快照与历史执行器 */
 public ProductImageService(AccessAuthorization access,PlatformTransactionManager manager,ProductV1Service products,ProductImageServiceSqlMapper statements){this.statements=statements;this.products=products;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** 只读图片字节；必须先通过商品读取权，不能凭图片UUID绕过租户。 */
 public record Content(String mediaType,byte[] bytes){}
 /** 图片列表不返回二进制；列表审计由统一投影提供。 */
 public Object list(VerifiedTenantContext c,String product){require(c,"read");product(c,product,false,null);return statements.statement1(c.tenantId().toString(),product);}
 /** 上传原始体最大5MiB，按解码器验证维度后重编码PNG去除原始元数据，最多10张；父CAS和图片同事务。 */
 public Object upload(VerifiedTenantContext c,String product,long version,byte[] bytes){require(c,"update");if(bytes==null||bytes.length<8||bytes.length>5242880)throw invalid();byte[] canonical;int width,height;
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();var reader=readers.next();try{String format=reader.getFormatName();if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase(Locale.ROOT)))throw invalid();reader.setInput(input,true,true);width=reader.getWidth(0);height=reader.getHeight(0);if(width<1||height<1||width>4096||height>4096||(long)width*height>16000000)throw invalid();var decoded=reader.read(0);var output=new ByteArrayOutputStream();if(!ImageIO.write(decoded,"png",output))throw invalid();canonical=output.toByteArray();if(canonical.length>5242880)throw invalid();}finally{reader.dispose();}}catch(IOException e){throw invalid();}
  final int w=width,h=height;return tx.execute(s->{product(c,product,true,version);var before=products.extensionSnapshot(c,product);if(statements.statement2(c.tenantId().toString(),product)>=10)throw invalid();String id=UUID.randomUUID().toString();try{statements.statement3(id,c.tenantId().toString(),product,canonical,java.security.MessageDigest.getInstance("SHA-256").digest(canonical),w,h,c.accountId().toString(),c.accountId().toString());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}bump(c,product,version);products.extensionEvent(c,product,"IMAGE_ADD",before);return Map.of("id",id,"version",version+1);});}
 /** 只提供已绑定本租户商品的图片，返回内容不含原始文件名/EXIF。 */
 public Content content(VerifiedTenantContext c,String product,String id){require(c,"read");product(c,product,false,null);uuid(id);var rows=statements.content(c.tenantId().toString(),product,id).stream().map(row->new Content((String)row.get("mediaType"),(byte[])row.get("bytes"))).toList();if(rows.size()!=1)throw changed();return rows.getFirst();}
 /** 解绑保留四审计和内容历史，不执行物理删除；仅停用/草稿可修改图片。 */
 public Object detach(VerifiedTenantContext c,String product,String id,long version){require(c,"update");uuid(id);return tx.execute(s->{product(c,product,true,version);var before=products.extensionSnapshot(c,product);if(statements.statement4(c.accountId().toString(),c.tenantId().toString(),product,id)!=1)throw changed();bump(c,product,version);products.extensionEvent(c,product,"IMAGE_DETACH",before);return Map.of("version",version+1);});}
 private void product(VerifiedTenantContext c,String id,boolean write,Long version){products.authorizeRecord(c,id,write?"update":"read",write);uuid(id);var rows=statements.parent(c.tenantId().toString(),id,write);if(rows.size()!=1)throw changed();var row=rows.getFirst();if(write)products.ensureNotInApproval(c,id);if(write&&(version==null||version<0||((Number)row.get("version")).longValue()!=version||"ENABLED".equals(row.get("status"))||row.get("archived")!=null||"LEGACY_PENDING".equals(row.get("structure"))))throw changed();}
 private void bump(VerifiedTenantContext c,String p,long v){if(statements.statement5(c.accountId().toString(),c.tenantId().toString(),p,v)!=1)throw changed();}
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_IMAGE_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"MASTER_DATA_CHANGED");}
}
