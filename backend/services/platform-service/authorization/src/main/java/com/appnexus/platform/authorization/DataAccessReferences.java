package com.appnexus.platform.authorization;

import java.util.*;

/** 数据归属选择公开端口；业务服务不得直接查询平台组织表。 */
public interface DataAccessReferences {
    /** @param id 同租户组织 UUID @param name 当前组织名称 */
    record Organization(UUID id,String name) {}
    /** 已登记的业务功能权限下读取本租户启用组织；结果不授予记录访问权。 */
    List<Organization> organizations(AccessAuthorization.Subject subject,String permissionCode);
}
