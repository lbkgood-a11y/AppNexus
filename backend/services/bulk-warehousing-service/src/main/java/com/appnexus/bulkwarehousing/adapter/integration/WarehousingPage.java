package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.contracts.businessobject.BusinessObjects.Operation;
import com.appnexus.contracts.businessobject.BusinessObjects.PageOperation;
import com.appnexus.contracts.businessobject.DedicatedOperations;

/** 过户及余额工作区与真实仓储操作的页面绑定。 */
final class WarehousingPage {
  private WarehousingPage() {}

  /** @param key 仓储只读菜单的固定子路由 @return 指向真实 GET 接口的同操作别名 */
  static PageOperation queryPage(String key,String zh,String en,String path) {
    String component="warehousing/"+key;
    return new PageOperation(component,"/"+component,zh,en,"GET",path);
  }

  static Operation operation(String object,String code,String zh,String en,String permission,String method,String path) {
    return DedicatedOperations.operation(object,code,zh,en,permission,
        new PageOperation("warehousing/coil-title","/warehousing/coil-title",zh,en,method,path));
  }
  static PageOperation flowPage(String zh,String en,String method,String path) {
    return new PageOperation("warehousing/coil-flow","/warehousing/coil-flow",zh,en,method,path);
  }

  static Operation flow(String object,String code,String zh,String en,String permission,String method,String path) {
    return DedicatedOperations.operation(object,code,zh,en,permission,flowPage(zh,en,method,path));
  }
  static PageOperation transitPage(String zh,String en,String method,String path) {
    return new PageOperation("warehousing/return-transit","/warehousing/return-transit",zh,en,method,path);
  }
}
