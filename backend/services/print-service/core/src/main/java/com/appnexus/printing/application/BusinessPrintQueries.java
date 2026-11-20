package com.appnexus.printing.application;
import java.util.*;
/** 业务打印固定查询输出端口，只允许本服务持久化适配器实现。 */
public interface BusinessPrintQueries {
 /** @param tenant 可信固定租户 @param contract 原业务声明的数据契约 @return 当前启用场景，不授予业务权限 */
 List<Map<String,Object>> scenes(Object tenant,Object contract);
 /** @param tenant 可信租户 @return 原服务权威渲染器在线事实，缺省不能放行 */
 Boolean rendererOnline(Object tenant);
 /** @param tenant 固定租户 @param source 已授权业务来源 @param actor 真实执行账号 @return 当前主体输出历史，不返回其他主体数据 */
 List<Map<String,Object>> history(Object tenant,Object source,Object actor);
}
