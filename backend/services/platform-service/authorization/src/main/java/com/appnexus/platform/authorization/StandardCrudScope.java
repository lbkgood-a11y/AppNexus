package com.appnexus.platform.authorization;

import java.util.List;
import java.util.UUID;

/** 标准对象的组织归属用例；复用功能、行范围与可信目标目录，业务只提供固定映射。 */
public final class StandardCrudScope {
    private final AccessPlanning planning;
    private final RowAccessControl rows;
    private final DataAccessReferences references;
    private final RowAccessControl.Mapping mapping;

    /** @param planning 权威计划 @param rows 公共执行器 @param references 启用组织目录 @param mapping 当前对象固定映射 */
    public StandardCrudScope(AccessPlanning planning,RowAccessControl rows,DataAccessReferences references,RowAccessControl.Mapping mapping){
        this.planning=planning;this.rows=rows;this.references=references;this.mapping=mapping;
    }

    /** @param subject 可信主体 @param action 原功能动作 @return 操作范围和字段计划 */
    public AccessPlan plan(AccessAuthorization.Subject subject,String action){
        return planning.plan(subject,mapping.resource()+":"+action,mapping.resource());
    }

    /** @param plan 本次请求计划 @return 固定列上映射后的绑定谓词 */
    public RowAccessControl.Predicate predicate(AccessPlan plan){
        var predicate=rows.predicate(plan,mapping);
        return new RowAccessControl.Predicate(predicate.sql(),predicate.parameters(),plan);
    }

    /** @param plan 已验证当前操作 @param record 修改时固定原记录，创建时为空 @param target 当前租户目标组织 @return 仅启用且属于范围时为真 */
    public boolean allowsTarget(AccessPlan plan,UUID record,UUID target){
        return organizations(plan,record).stream().anyMatch(item->item.id().equals(target));
    }

    /** @param plan 本次操作计划 @param record 候选查询或新增时为空 @return 本租户可选启用组织 */
    public List<DataAccessReferences.Organization> organizations(AccessPlan plan,UUID record){
        var range=rows.organizationRangeForRecord(plan,"organizationId",record);
        return references.organizations(plan.subject(),plan.permissionCode()).stream()
            .filter(entry->range.all()||range.ids().contains(entry.id().toString())).toList();
    }
}
