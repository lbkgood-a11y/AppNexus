package com.appnexus.printing.application;
import java.util.*;
/** 业务打印固定查询输出端口，只允许本服务持久化适配器实现。 */
public interface BusinessPrintQueries {
 /**
  * 查询原业务契约可用的打印场景。
  * @param tenant 可信租户 UUID 字符串，不接受客户端覆盖
  * @param contract 原业务声明的数据契约编码，按原规则精确匹配
  * @return 当前启用场景，不授予业务权限
  */
 List<Map<String,Object>> scenes(String tenant,String contract);
 /**
  * 查询当前租户的渲染器在线事实。
  * @param tenant 可信租户 UUID 字符串，不接受客户端覆盖
  * @return 原服务权威渲染器在线事实，缺省不能放行
  */
 Boolean rendererOnline(String tenant);
 /**
  * 查询当前主体的业务打印历史。
  * @param tenant 可信租户 UUID 字符串，不接受客户端覆盖
  * @param source 已授权业务来源标识，保持原来源协议
  * @param actor 真实执行账号 UUID 字符串，来自可信上下文
  * @return 当前主体输出历史，不返回其他主体数据
  */
 List<Map<String,Object>> history(String tenant,String source,String actor);
}
