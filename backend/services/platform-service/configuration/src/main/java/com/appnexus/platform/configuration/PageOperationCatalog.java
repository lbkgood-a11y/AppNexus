package com.appnexus.platform.configuration;
import java.util.List;
/** 页面操作权限的构建内权威目录；适配器不得重新维护页面到权限的运行时列表。 */
public interface PageOperationCatalog {
 /**
  * 返回该页面显式接口契约涉及的功能权限，不表示调用者已经获得授权。
  * @param page 构建内页面标识，未知页面返回空集合
  * @return 去重且稳定排序的权限码，保留实际表达式的所有分支用于展示
  */
 List<String> permissions(String page);
}
