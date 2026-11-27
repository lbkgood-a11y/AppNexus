package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundSiteCommandService;
import com.appnexus.contracts.businessobject.BusinessObjectRuntime;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.tenant.TenantAccess;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** 作业票固定打印事实适配，设备指令与打印输出仍由各自原用例处理。 */
public final class InboundTaskPrintRuntime implements BusinessObjectRuntime {
    /** 数据契约来自唯一对象声明，供现有场景、模板及输出驱动识别。 */
    public static final String CONTRACT="warehousing.inbound.task-slip";
    /** 作业票执行继续使用原现场打印操作权限。 */
    public static final String OPERATION="print_site_ticket";

    private final Supplier<InboundSiteCommandService> site;
    private final Supplier<TenantAccess> tenants;

    /** @param site 原作业票准入及作业状态用例 @param tenants 当前租户成员目录 */
    public InboundTaskPrintRuntime(Supplier<InboundSiteCommandService> site,Supplier<TenantAccess> tenants){
        this.site=site;this.tenants=tenants;
    }

    @Override public Snapshot read(Context context,String type,String objectId,String operation,long basisVersion,Set<String> fields){
        if(!"BulkInboundTask".equals(type)||!OPERATION.equals(operation)||!Set.of("taskNo").equals(fields))
            throw new Rejection(403,"BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
        UUID actor,tenant,task;
        try{
            actor=UUID.fromString(context.actorId());tenant=UUID.fromString(context.tenantId());task=UUID.fromString(objectId);
        }catch(IllegalArgumentException|NullPointerException invalid){
            throw new Rejection(400,"INBOUND_TASK_ID_INVALID");
        }
        // 元数据启动校验只读声明，避免授权目录与业务用例反向装配。
        var directory=tenants.get();
        var commandSite=site.get();
        if(directory==null||commandSite==null)throw new Rejection(503,"BUSINESS_OBJECT_EXECUTION_UNAVAILABLE");
        var member=directory.available(actor).stream().filter(value->tenant.equals(value.tenantId())).findFirst()
            .orElseThrow(()->new Rejection(403,"INBOUND_SITE_COMMAND_ACCESS_DENIED"));
        if(!directory.isActiveMember(tenant,member.memberId()))throw new Rejection(403,"INBOUND_SITE_COMMAND_ACCESS_DENIED");
        var trusted=new VerifiedTenantContext(actor,tenant,member.memberId(),1,
            optionalId(context.personId()),optionalId(context.organizationId()));
        var ticket=commandSite.lockPrintableTicket(trusted,task);
        if(ticket.version()!=basisVersion)throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
        return new Snapshot(ticket.version(),null,Map.of("taskNo",ticket.taskNo()),task.toString());
    }

    @Override public Result execute(Context context,String type,String objectId,String operation,long basisVersion,Map<String,?> values){
        throw new Rejection(403,"BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
    }

    private static UUID optionalId(String id){return id==null?null:UUID.fromString(id);}
}
