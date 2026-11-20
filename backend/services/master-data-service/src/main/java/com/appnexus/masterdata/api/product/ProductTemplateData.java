package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductTemplateData {
 private ProductTemplateData(){}
/** 模板创建：code为租户内唯一编码，name为1至200字符，purpose仅BASIC/QUALITY/EXTENSION，version新建0。 */
 public record TemplateInput(String code,String name,String purpose,long version){}
/** 模板项固定attributeId/revisionId，required为启用必填；searchable仅支持已实现类型，自定义输入尚未接入时必须false。 */
 public record ItemInput(String attributeId,String revisionId,String groupCode,String groupName,boolean required,boolean searchable,boolean allowCustom,int sortOrder){}
/** 版本草稿：description最多1000字符，items固定已发布属性修订；version为当前模板父CAS。 */
 public record VersionInput(String description,List<ItemInput> items,long version){}
}
