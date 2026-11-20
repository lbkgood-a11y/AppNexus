package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductImageData {
 private ProductImageData(){}
/** 只读图片字节；必须先通过商品读取权，不能凭图片UUID绕过租户。 */
 public record Content(String mediaType,byte[] bytes){}
}
