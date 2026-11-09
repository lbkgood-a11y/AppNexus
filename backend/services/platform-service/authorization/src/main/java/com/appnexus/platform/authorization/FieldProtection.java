package com.appnexus.platform.authorization;

import java.util.Map;

/** 集中字段策略判定与投影端口；原文只在服务端，业务用例不实现自己的遮盖算法。 */
public interface FieldProtection {
    /**
     * 查询主体的强制字段限制，多个有效角色取最严格模式。
     * @param subject 可信主体；上下文无效时不得回退原文
     * @param resource 目录中的稳定资源名
     * @return 字段到展示模式的只读映射，不含原值
     */
    Map<String,FieldSecurity.Mode> restrictions(AccessAuthorization.Subject subject,String resource);

    /**
     * 递归处理响应中的记录、分页、集合和映射；字段目录与策略从统一端口读取。
     * @param subject 可信执行主体，租户身份不能由响应值推导
     * @param value 服务端原始响应，不得记录其中的敏感值
     * @return 同结构的新对象，原对象不被修改，受限原文不会进入响应
     */
    <T> T protect(AccessAuthorization.Subject subject,T value);
}
