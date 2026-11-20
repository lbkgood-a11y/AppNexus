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
     * 投影已授权记录的契约字段，仅输出资源声明字段；不授予行或功能权限。
     * @param subject 当前可信主体，不得跨请求缓存
     * @param resource 同一对象声明的资源编码
     * @param values 服务端原值，禁止记录日志
     * @return 不可变投影；数值不得被字符串脱敏隐式转换，隐藏字段不返回
     */
    Map<String,Object> project(AccessAuthorization.Subject subject,String resource,Map<String,?> values);

    /**
     * 递归处理响应中的记录、分页、集合和映射；字段目录与策略从统一端口读取。
     * @param subject 可信执行主体，租户身份不能由响应值推导
     * @param value 服务端原始响应，不得记录其中的敏感值
     * @return 同结构的新对象，原对象不被修改，受限原文不会进入响应
     */
    <T> T protect(AccessAuthorization.Subject subject,T value);
}
