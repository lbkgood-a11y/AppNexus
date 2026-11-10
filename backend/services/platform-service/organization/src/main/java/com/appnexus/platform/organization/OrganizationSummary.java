package com.appnexus.platform.organization;

import com.appnexus.platform.authorization.AccessAuthorization;
import java.util.List;

/** 组织类型固定联表聚合样例；先限制可信租户和行范围，再聚合，不提供自由报表或任意 SQL。 */
public interface OrganizationSummary {
    /** 公开分组统计；只统计操作者有权读取的组织，空分组不泄露无权组织存在性。 */
    record Group(String typeCode,String typeName,boolean enabled,long count){}
    /**
     * @param subject 服务端验证的账号/租户/成员，不能由客户端提供任意租户
     * @return 有权组织按已声明类型及启停状态聚合；DENY 为空列表
     */
    List<Group> summarize(AccessAuthorization.Subject subject);
}
