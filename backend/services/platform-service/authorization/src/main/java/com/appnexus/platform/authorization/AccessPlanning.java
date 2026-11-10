package com.appnexus.platform.authorization;

/** 统一权限计划端口；业务查询和写入必须按操作重新解析，不能复用菜单或角色展示快照。 */
public interface AccessPlanning {
    /**
     * 从同一权威读取事务解析功能、行和字段限制。
     * @param subject 服务端已验证租户主体，不能由请求体指定
     * @param permissionCode 本次资源操作的稳定权限码
     * @param resource 服务端登记资源，当前正式接入 organization.unit
     * @return 不可变计划，缺少功能权限或数据范围时失败关闭
     * @throws SecurityException 主体、资源或功能权限不成立
     */
    AccessPlan plan(AccessAuthorization.Subject subject,String permissionCode,String resource);
}
