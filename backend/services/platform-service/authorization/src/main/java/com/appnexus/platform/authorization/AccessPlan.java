package com.appnexus.platform.authorization;

import java.time.Instant;
import java.util.*;

/**
 * 单次执行的不可变权限计划；只承载服务端决策，不可由 HTTP 请求反序列化或跨请求复用。
 * 行范围由受控仓储编译，字段强制限制只负责展示，不隐式授予编辑、查询、排序、导出或打印。
 * @param subject 当前可信账号/租户/成员
 * @param permissionCode 本次唯一操作权限码，不可跨授权分支拼接
 * @param resource 服务端登记资源标识，不是表名或 SQL
 * @param rows 同一操作的结构化行范围，DENY 必须产生空结果或拒绝写入
 * @param fields 该主体当前强制字段限制，多角色按最严格规则合并
 * @param evaluatedAt 服务端本次判定时间，UTC；不能作为后续请求权限缓存
 */
public record AccessPlan(AccessAuthorization.Subject subject,String permissionCode,String resource,
        DataScopeAuthorization.DataScope rows,Map<String,FieldSecurity.Mode> fields,Instant evaluatedAt) {
    /** 深复制可变集合，拒绝不完整主体或未知权限形状。 */
    public AccessPlan {
        Objects.requireNonNull(subject);Objects.requireNonNull(subject.accountId());
        if(subject.tenantId()==null||subject.memberId()==null)throw new IllegalArgumentException("AccessPlan requires tenant subject");
        if(resource==null||!resource.matches("[a-z][a-z0-9.]*")||permissionCode==null||!permissionCode.startsWith(resource+":"))
            throw new IllegalArgumentException("Permission does not belong to resource");
        Objects.requireNonNull(rows);fields=Map.copyOf(fields);Objects.requireNonNull(evaluatedAt);
        rows=new DataScopeAuthorization.DataScope(rows.type(),Set.copyOf(rows.organizationIds()));
    }
}
