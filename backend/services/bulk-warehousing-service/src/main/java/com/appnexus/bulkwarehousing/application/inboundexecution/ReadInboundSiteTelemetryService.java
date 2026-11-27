package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundSiteTelemetry;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** 通过原工作台和计量权限读现场快照，盲检期间不返回抓拍。 */
public final class ReadInboundSiteTelemetryService implements ReadInboundSiteTelemetry {
    private final ReadInboundWorkbenchDetailUseCase details;
    private final AccessPlanning access;
    private final FieldProtection fields;
    private final InboundSiteTelemetrySource source;
    private final Clock clock;

    /** @param details 可信作业及盲检状态 @param access 公共权限 @param fields 字段策略
     * @param source 已配置现场来源 @param clock UTC 时间源 */
    public ReadInboundSiteTelemetryService(ReadInboundWorkbenchDetailUseCase details,AccessPlanning access,
                                           FieldProtection fields,InboundSiteTelemetrySource source,Clock clock) {
        this.details=details;this.access=access;this.fields=fields;this.source=source;this.clock=clock;
    }

    /** {@inheritDoc} */
    @Override public InboundSiteReading read(VerifiedTenantContext context,UUID task) {
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),
                context.personId(),context.organizationId());
        if(!allowed(subject,ReadInboundWorkbenchDetailUseCase.METER,RegisterExternalMeasurementUseCase.RESOURCE)) {
            throw new Rejection(403,"INBOUND_SITE_ACCESS_DENIED");
        }
        var detail=details.detail(context,task);
        boolean capture=!detail.blind() && allowed(subject,ReadInboundWorkbenchDetailUseCase.IDENTITY,ReadInboundWorkbenchUseCase.RESOURCE)
                && fields.restrictions(subject,ReadInboundWorkbenchUseCase.RESOURCE).isEmpty();
        var reading=source.latest(context.tenantId(),task,capture);
        if(reading.isEmpty())return empty("UNAVAILABLE");
        var snap=reading.get();
        var age=Duration.between(snap.observedAt(),clock.instant());
        if(age.compareTo(Duration.ofSeconds(10))>0 || age.compareTo(Duration.ofSeconds(-2))<0)return empty("STALE");
        return new InboundSiteReading("LIVE",source.simulated(),snap.station(),snap.kg(),snap.stable(),snap.observedAt(),snap.capture());
    }

    private InboundSiteReading empty(String state){
        return new InboundSiteReading(state,source.simulated(),null,null,null,null,null);
    }

    private boolean allowed(Subject subject,String permission,String resource){
        try {
            var plan=access.plan(subject,permission,resource);
            return plan.tenantWide()&&subject.equals(plan.subject())&&permission.equals(plan.permissionCode());
        } catch(SecurityException denied) {
            return false;
        }
    }
}
