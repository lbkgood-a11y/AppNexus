package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.contracts.businessobject.BusinessObjects.PageOperation;

/** 仓储查询的固定页面绑定。 */
final class WarehousingPage {
  private WarehousingPage() {}

  /** @param key 仓储只读菜单的固定子路由 @return 指向真实 GET 接口的同操作别名 */
  static PageOperation queryPage(String key,String zh,String en,String path) {
    String component="warehousing/"+key;
    return new PageOperation(component,"/"+component,zh,en,"GET",path);
  }

}
