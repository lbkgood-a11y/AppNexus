package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** 只组装实际获准的工作台卡片。盲检判断在查询身份之前执行。 */
public final class ReadInboundWorkbenchDetailService implements ReadInboundWorkbenchDetailUseCase {
    private final AccessPlanning access;
    private final FieldProtection fields;
    private final InboundWorkbenchDetailQueries queries;

    /** @param access 统一权限 @param fields 统一字段保护 @param queries 受控聚合读模型 */
    public ReadInboundWorkbenchDetailService(AccessPlanning access,FieldProtection fields,InboundWorkbenchDetailQueries queries) {
        this.access=access;this.fields=fields;this.queries=queries;
    }

    /** {@inheritDoc} */
    @Override public WorkbenchDetail detail(VerifiedTenantContext context,UUID id) {
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var root=detailPlan(subject);
        var identityPlan=permitted(subject,IDENTITY,ReadInboundWorkbenchUseCase.RESOURCE);
        var qualityPlan=permitted(subject,QUALITY,RegisterInboundSampleUseCase.RESOURCE);
        var meterPlan=permitted(subject,METER,RegisterExternalMeasurementUseCase.RESOURCE);
        var receiptPlan=permitted(subject,RECEIPT,CreateBulkReceiptDraftUseCase.RESOURCE);
        return queries.read(()->{
            var task=requiredTask(root,id);
            Map<String,Object> identity=task.blind()?Map.of():read(identityPlan,
                    plan->fields.project(subject,ReadInboundWorkbenchUseCase.RESOURCE,queries.identity(plan,id)));
            var quality=read(qualityPlan,plan->queries.quality(plan,id));
            var metering=read(meterPlan,plan->queries.metering(plan,id));
            var receipt=read(receiptPlan,plan->queries.receipt(plan,id));
            var totals=new LinkedHashMap<String,Object>();
            for(String key:new String[]{"physicalNetKg","theoreticalKg","varianceRatio"}) {
                if(metering.containsKey(key))totals.put(key,metering.get(key));
            }
            for(String key:new String[]{"deductionKg","accountingKg","qualityState"}) {
                if(receipt.containsKey(key))totals.put(key,receipt.get(key));
            }
            var risk=task.unload()==null?Map.<String,Object>of():Map.<String,Object>of("unloadDisposition",task.unload());
            return new WorkbenchDetail(id,task.version(),task.stage(),task.status(),task.blind(),
                    identity,quality,metering,receipt,Map.copyOf(totals),risk);
        });
    }

    /** {@inheritDoc} */
    @Override public Map<String,Object> card(VerifiedTenantContext context,UUID id,Card card) {
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var root=detailPlan(subject);
        var selected=switch(card) {
            case IDENTITY -> permitted(subject,IDENTITY,ReadInboundWorkbenchUseCase.RESOURCE);
            case QUALITY -> permitted(subject,QUALITY,RegisterInboundSampleUseCase.RESOURCE);
            case METERING -> permitted(subject,METER,RegisterExternalMeasurementUseCase.RESOURCE);
            case RECEIPT -> permitted(subject,RECEIPT,CreateBulkReceiptDraftUseCase.RESOURCE);
        };
        if(selected==null)throw new Rejection(403,"INBOUND_QUEUE_ACCESS_DENIED");
        return queries.read(()->{
            var task=requiredTask(root,id);
            return switch(card) {
                case IDENTITY -> task.blind()?Map.of():read(selected,
                        plan->fields.project(subject,ReadInboundWorkbenchUseCase.RESOURCE,queries.identity(plan,id)));
                case QUALITY -> read(selected,plan->queries.quality(plan,id));
                case METERING -> read(selected,plan->queries.metering(plan,id));
                case RECEIPT -> read(selected,plan->queries.receipt(plan,id));
            };
        });
    }

    private AccessPlan detailPlan(Subject subject) {
        var root=access.plan(subject,DETAIL,ReadInboundWorkbenchUseCase.RESOURCE);
        if(!root.tenantWide()||!subject.equals(root.subject()))throw new Rejection(403,"INBOUND_QUEUE_ACCESS_DENIED");
        return root;
    }

    private InboundWorkbenchDetailQueries.Task requiredTask(AccessPlan root,UUID id) {
        var task=queries.task(root,id);
        if(task==null)throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
        return task;
    }
    /** 可选权限失败发生在聚合事务之外，避免公共权限代理将其它授权查询标记回滚。 */
    private AccessPlan permitted(Subject subject,String permission,String resource) {
        AccessPlan plan;
        try{plan=access.plan(subject,permission,resource);}
        catch(SecurityException denied){return null;}
        return plan.tenantWide()&&subject.equals(plan.subject())?plan:null;
    }

    private static Map<String,Object> read(AccessPlan plan,Function<AccessPlan,Map<String,Object>> query) {
        return plan==null?Map.of():query.apply(plan);
    }
}
