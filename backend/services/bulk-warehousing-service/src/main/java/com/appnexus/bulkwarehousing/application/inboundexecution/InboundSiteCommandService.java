package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** 现场指令只处理车次设备，不变更业务状态或库存。 */
public final class InboundSiteCommandService implements InboundSiteCommands {
    private static final Set<String> ACTIVE=Set.of("ARRIVED","WORKING","READY_TO_RECEIVE");
    private final InboundWorkbenchDetailQueries queries;
    private final AccessPlanning access;
    private final InboundSiteCommandChannel channel;
    private final Clock clock;
    private final Duration timeout;

    /** @param queries 作业授权投影 @param access 平台操作授权 @param channel 设备适配器
     * @param clock UTC 时钟 @param timeout 等待回执时长 */
    public InboundSiteCommandService(InboundWorkbenchDetailQueries queries,AccessPlanning access,
                                     InboundSiteCommandChannel channel,Clock clock,Duration timeout){
        this.queries=queries;this.access=access;this.channel=channel;this.clock=clock;this.timeout=timeout;
    }

    /** {@inheritDoc} */
    @Override public InboundSiteCommandStatus send(VerifiedTenantContext context,UUID task,Action action,InboundSiteCommandRequest input){
        if(input==null||input.commandId()==null)throw new Rejection(400,"INBOUND_SITE_COMMAND_INVALID");
        var view=requireTask(context,task,action);
        requireActive(view.status());
        if(!channel.configured())throw new Rejection(503,"INBOUND_SITE_CHANNEL_DISABLED");
        var now=clock.instant();
        var job=channel.find(context.tenantId(),task,input.commandId());
        if(job==null) {
            if(input.retry())throw new Rejection(404,"INBOUND_SITE_COMMAND_NOT_FOUND");
            job=new InboundSiteCommandChannel.Job(context.tenantId(),task,input.commandId(),action,view.taskNo(),
                    context.accountId(),now);
            channel.offer(job,context.accountId(),now,now.plus(timeout),null);
        } else {
            if(job.action()!=action)throw new Rejection(409,"INBOUND_SITE_COMMAND_CONFLICT");
            var previous=result(job);
            if(input.retry() && "TIMED_OUT".equals(previous.state()))
                channel.offer(job,context.accountId(),now,now.plus(timeout),previous.sentAt());
        }
        return result(job);
    }

    /** {@inheritDoc} */
    @Override public InboundSiteCommandStatus status(VerifiedTenantContext context,UUID task,UUID id){
        requireTask(context,task,null);
        var job=channel.find(context.tenantId(),task,id);
        if(job==null)throw new Rejection(404,"INBOUND_SITE_COMMAND_NOT_FOUND");
        require(context,job.action().permission());
        return result(job);
    }

    /**
     * @param context 平台验证的本租户成员
     * @param task 作业 UUID
     * @return 原打印及详情准入下的作业票快照，锁随公共执行写事务提交
     */
    public InboundWorkbenchDetailQueries.Ticket lockPrintableTicket(VerifiedTenantContext context,UUID task){
        var print=require(context,PRINT);
        require(context,com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase.DETAIL);
        var ticket=queries.read(()->queries.lockTicket(print,task));
        if(ticket==null)throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
        requireActive(ticket.status());
        return ticket;
    }

    private static void requireActive(String status){
        if(!ACTIVE.contains(status))throw new Rejection(409,"INBOUND_SITE_TASK_INACTIVE");
    }

    private InboundWorkbenchDetailQueries.Task requireTask(VerifiedTenantContext context,UUID id,Action action) {
        if(action!=null)require(context,action.permission());
        var root=require(context,com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase.DETAIL);
        var task=queries.read(()->queries.task(root,id));
        if(task==null)throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
        return task;
    }

    private com.appnexus.platform.authorization.AccessPlan require(VerifiedTenantContext context,String permission){
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var plan=access.plan(subject,permission,ReadInboundWorkbenchUseCase.RESOURCE);
        if(!plan.tenantWide()||!subject.equals(plan.subject())||!permission.equals(plan.permissionCode()))
            throw new Rejection(403,"INBOUND_SITE_COMMAND_ACCESS_DENIED");
        return plan;
    }

    private InboundSiteCommandStatus result(InboundSiteCommandChannel.Job job){
        var attempted=channel.attemptedAt(job);
        var ack=channel.acknowledgement(job);
        if(ack!=null && ack.acknowledgedAt().isAfter(clock.instant().plusSeconds(2)))ack=null;
        var status=ack!=null?ack.state():clock.instant().isBefore(attempted.plus(timeout))?"PENDING":"TIMED_OUT";
        return new InboundSiteCommandStatus(job.commandId(),job.taskId(),job.action(),status,attempted,
                ack!=null?ack.acknowledgedAt():null,channel.simulated());
    }
}
