package com.appnexus.masterdata.product;
import com.appnexus.contracts.operation.*;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
/** 商品图片真实二进制入口，沿用可信会话和CSRF；从不将图片作为认证凭据。 */
@RestController @RequestMapping("/api/master-data/product/{product}/images")
public final class ProductImageController {
 private final ProductImageService service;
 /** @param service 负责解码、租户、CAS、审计和存储事务的执行器 */
 public ProductImageController(ProductImageService service){this.service=service;}
 /** 当前商品授权范围内图片元数据，标准四审计投影。 */
 @GetMapping @RecordAuditSource(table="md_product_image")
 @OperationBinding(id="Product.listImages",objectType="Product",operationCode="listImages",nameZh="查询商品图片",nameEn="List images",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="原商品读取权及可信租户")
 public Object list(HttpServletRequest r,@PathVariable String product){return service.list(context(r),product);}
 /** 原始请求体最大5MiB；先限制读取再验证解码，不依赖multipart临时磁盘。 */
 @PostMapping(consumes={"image/png","image/jpeg","application/octet-stream"})
 @OperationBinding(id="Product.uploadImage",objectType="Product",operationCode="uploadImage",nameZh="上传商品图片",nameEn="Upload image",pages={"master-data/product"},permissions={"masterdata.product:update"},policy="商品写权限、父CAS及停用/草稿状态")
 public Object upload(HttpServletRequest r,@PathVariable String product,@RequestParam long version)throws java.io.IOException{return service.upload(context(r),product,version,r.getInputStream().readNBytes(5242881));}
 /** no-store、nosniff及固定PNG内容类型，禁止跨租户直链和SVG脚本。 */
 @GetMapping("/{id}/content")
 @OperationBinding(id="Product.readImage",objectType="Product",operationCode="readImage",nameZh="查看商品图片",nameEn="Read image",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="每次读取复核原商品权限")
 public ResponseEntity<byte[]> content(HttpServletRequest r,@PathVariable String product,@PathVariable String id){var file=service.content(context(r),product,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(file.mediaType())).body(file.bytes());}
 /** 解绑保留历史内容，父版本必填，不开放物理删除入口。 */
 @PutMapping("/{id}/detach")
 @OperationBinding(id="Product.detachImage",objectType="Product",operationCode="detachImage",nameZh="移除商品图片",nameEn="Detach image",pages={"master-data/product"},permissions={"masterdata.product:update"},policy="停用/草稿、父CAS与追加历史")
 public Object detach(HttpServletRequest r,@PathVariable String product,@PathVariable String id,@RequestBody ProductV1Service.Version v){if(v==null||v.version()==null)throw new MasterData.Rejection(400,"PRODUCT_INPUT_INVALID");return service.detach(context(r),product,id,v.version());}
 private static VerifiedTenantContext context(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return c;}
}
