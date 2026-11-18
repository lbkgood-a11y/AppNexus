package com.appnexus.masterdata.product;
import com.appnexus.contracts.operation.*;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
/** 现有单位对象下的换算操作，沿用Cookie/CSRF及可信租户，不登记第二套单位对象。 */
@RestController @RequestMapping("/api/master-data/product-unit/conversions")
public final class ProductUnitConversionController {
 private final ProductUnitConversionService service;
 /** @param service 精确数值、租户、同维度约束及事务执行器 */
 public ProductUnitConversionController(ProductUnitConversionService service){this.service=service;}
 /** 查询本租户关系及标准四审计投影。 */
 @GetMapping @RecordAuditSource(table="md_product_unit_conversion")
 @OperationBinding(id="ProductUnit.listConversions",objectType="ProductUnit",operationCode="listConversions",nameZh="查询单位换算",nameEn="List conversions",pages={"master-data/product-unit"},permissions={"masterdata.productunit:read"},policy="可信租户单位读取权限")
 public Object list(HttpServletRequest r){return service.list(context(r));}
 /** 创建不可变系数关系，不自动补反向关系。 */
 @PostMapping @OperationBinding(id="ProductUnit.createConversion",objectType="ProductUnit",operationCode="createConversion",nameZh="新增单位换算",nameEn="Create conversion",pages={"master-data/product-unit"},permissions={"masterdata.productunit:convert"},policy="独立换算维护权、同租户同维度及事务")
 public Object create(HttpServletRequest r,@RequestBody ProductUnitConversionService.Input i){return service.create(context(r),i);}
 /** 当前关系CAS启停，创建审计不变。 */
 @PutMapping("/{id}/status") @OperationBinding(id="ProductUnit.setConversionStatus",objectType="ProductUnit",operationCode="setConversionStatus",nameZh="启停单位换算",nameEn="Set conversion status",pages={"master-data/product-unit"},permissions={"masterdata.productunit:convert"},policy="独立权限和关系CAS")
 public Object status(HttpServletRequest r,@PathVariable String id,@RequestBody ProductUnitConversionService.Status i){return service.status(context(r),id,i);}
 /** 只读计算，数量必须为十进制字符串；返回精确值及展示舍入值。 */
 @GetMapping("/{id}/calculate") @OperationBinding(id="ProductUnit.calculateConversion",objectType="ProductUnit",operationCode="calculateConversion",nameZh="计算单位换算",nameEn="Calculate conversion",pages={"master-data/product-unit"},permissions={"masterdata.productunit:read"},policy="仅已授权同租户启用关系")
 public Object calculate(HttpServletRequest r,@PathVariable String id,@RequestParam String quantity){return service.calculate(context(r),id,quantity);}
 private static VerifiedTenantContext context(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return c;}
}
