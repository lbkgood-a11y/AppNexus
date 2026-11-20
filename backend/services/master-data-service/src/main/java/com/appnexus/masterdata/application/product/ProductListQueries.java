package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.application.product.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
/** 商品列表输出端口；只供已授权读取调用，不接受任意SQL、列名或JSONPath。 */
public interface ProductListQueries {
 /** @param c 可信主体 @param name 字面包含最多200字 @param code 字面包含最多64字 @param categoryId 可空分类UUID @param form 可空商品形式 @param status 可空生命周期状态 @param archived 是否只看归档 @param page 1–1000000 @param size 1–100 @param configId 动态条件必须绑定的固定配置UUID @param filtersJson 最多5000字符、三项AND条件；固定类型校验 @return 同一租户和条件下的真实总数及分页，稳定按code/id排序 */
 Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson);
}
