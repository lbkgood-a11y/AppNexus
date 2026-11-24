package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.domain.inboundexecution.MeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.QualityGateStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.StandardMeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.TemplateQualityGate;
import com.appnexus.bulkwarehousing.domain.inboundexecution.UnloadingFact;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 毛重、监卸和皮重按同一作业锁串行；退车保留双读数但不产生有效实收量。 */
public final class GroundWeighingService implements GroundWeighingUseCase {
    private static final Set<String> SCALE = Set.of("SCALE", "SCALE_DIFFERENCE", "SCALE_OR_EXTERNAL");
    private static final Set<String> QUALITY = Set.of("SAMPLED", "QUALIFIED_AND_UNLOADED");
    private static final Set<String> NOTICES = Set.of("ISSUED", "RECEIVING");
    private final AccessPlanning access;
    private final GroundWeighingRepository store;

    /** @param access 统一权限计划 @param store 作业锁、票据和审计仓储 */
    public GroundWeighingService(AccessPlanning access, GroundWeighingRepository store) {
        this.access = access;
        this.store = store;
    }

    /** {@inheritDoc} */
    @Override public GroundWeighingResult gross(VerifiedTenantContext context, Gross command) {
        var plan = require(context,GROSS,RegisterExternalMeasurementUseCase.RESOURCE);
        var tenant=context.tenantId(); var actor=context.accountId();
        return store.transaction(plan, () -> {
            var task=task(tenant,command.taskId(),command.taskVersion());
            if (!Set.of("ARRIVED","WORKING").contains(task.status()) || store.lockMeter(tenant,command.taskId())!=null)
                throw reject("INBOUND_GROSS_STATE_CONFLICT");
            if (!identifier(command.meteringNo(),64)) throw bad();
            ticket(command.ticket(),task);
            var meter=UUID.randomUUID();
            store.saveGross(tenant,actor,command.taskId(),meter,command.meteringNo(),command.ticket());
            String queue = "QUALIFIED_AND_UNLOADED".equals(task.qualityStrategy()) ? "UNLOADING" : "WAIT_TARE";
            advance(tenant,actor,command.taskId(),task.version(),new GroundWeighingRepository.Advance("WORKING",queue));
            return new GroundWeighingResult(meter,0,task.version()+1,"GROSS_RECORDED",null);
        });
    }

    /** {@inheritDoc} */
    @Override public GroundWeighingResult unload(VerifiedTenantContext context, Unload command) {
        var plan=require(context,UNLOAD,ReadInboundWorkbenchUseCase.RESOURCE);
        var tenant=context.tenantId(); var actor=context.accountId();
        return store.transaction(plan, () -> {
            var task=task(tenant,command.taskId(),command.taskVersion());
            var meter=grossMeter(tenant,command.taskId(),command.meterVersion());
            if (!"WORKING".equals(task.status()) || !"QUALIFIED_AND_UNLOADED".equals(task.qualityStrategy())
                    || task.unloading().disposition()!=UnloadingFact.Disposition.UNCONFIRMED)
                throw reject("INBOUND_UNLOAD_STATE_CONFLICT");
            UnloadingFact fact;
            try {
                fact=new UnloadingFact(UnloadingFact.Disposition.valueOf(command.outcome().name()),
                        UUID.randomUUID(),command.extraDeductionKg());
            } catch (IllegalArgumentException|NullPointerException|ArithmeticException e) {
                throw bad();
            }
            if (fact.extraDeductionKg().compareTo(meter.grossKg())>=0) throw bad();
            String state=command.outcome()==UnloadOutcome.RETURN ? "RETURN_PENDING" : "WORKING";
            if (store.saveUnload(tenant,actor,command.taskId(),task.version(),fact,
                    new GroundWeighingRepository.Advance(state,"WAIT_TARE"))!=1) throw reject("INBOUND_METER_VERSION_CONFLICT");
            return new GroundWeighingResult(meter.id(),meter.version(),task.version()+1,meter.status(),null);
        });
    }

    /** {@inheritDoc} */
    @Override public GroundWeighingResult tare(VerifiedTenantContext context, Tare command) {
        var plan=require(context,TARE,RegisterExternalMeasurementUseCase.RESOURCE);
        var tenant=context.tenantId(); var actor=context.accountId();
        return store.transaction(plan, () -> {
            var task=task(tenant,command.taskId(),command.taskVersion());
            var meter=grossMeter(tenant,command.taskId(),command.meterVersion());
            if (!Set.of("WORKING","RETURN_PENDING").contains(task.status())) throw reject("INBOUND_TARE_STATE_CONFLICT");
            ticket(command.ticket(),task);
            if (!meter.station().equalsIgnoreCase(command.ticket().station()) || !command.ticket().observedAt().isAfter(meter.grossAt()))
                throw bad();
            boolean returned=task.unloading().disposition()==UnloadingFact.Disposition.RETURN;
            if (returned!="RETURN_PENDING".equals(task.status())) throw reject("INBOUND_TARE_STATE_CONFLICT");

            UUID qualityId=null;
            if (!returned) {
                var quality=Optional.ofNullable(store.lockQuality(tenant,command.taskId())).map(value ->
                        new QualityGateStrategy.QualityFact(value.id(),value.version(),value.sampled(),
                                "PASS".equals(value.decision()),"FAIL".equals(value.decision()),false,false));
                var gate=new TemplateQualityGate(TemplateQualityGate.Mode.valueOf(task.qualityStrategy()))
                        .evaluate(QualityGateStrategy.Point.CAPTURE_TARE,quality,task.unloading());
                if (gate.outcome()==QualityGateStrategy.Outcome.BLOCK) throw reject(gate.reasonCode());
                qualityId=gate.qualityId().orElse(null);
            }
            var method=MeteringStrategy.Method.SCALE_DIFFERENCE;
            BigDecimal net;
            try {
                var evidence=new MeteringStrategy.Evidence(meter.id(),method,meter.grossKg(),command.ticket().kg(),null,null,null);
                net=new StandardMeteringStrategy().evaluate(evidence,new MeteringStrategy.Rule(method,false,null),
                        task.unloading()).map(MeteringStrategy.Result::postingBasisKg).orElse(null);
            } catch (IllegalArgumentException e) {
                throw bad();
            }
            if (store.saveTare(tenant,actor,command.taskId(),meter,command.ticket(),qualityId,net)!=1)
                throw reject("INBOUND_METER_VERSION_CONFLICT");
            advance(tenant,actor,command.taskId(),task.version(),returned
                    ? new GroundWeighingRepository.Advance("RETURNED",null)
                    : new GroundWeighingRepository.Advance("READY_TO_RECEIVE","WAIT_RECEIPT"));
            return new GroundWeighingResult(meter.id(),meter.version()+1,task.version()+1,
                    returned ? "RETURN_RECORDED" : "CONFIRMED",net==null ? null : net.toPlainString());
        });
    }

    private AccessPlan require(VerifiedTenantContext c,String permission,String resource) {
        var subject=new Subject(c.accountId(),c.tenantId(),c.memberId(),c.personId(),c.organizationId());
        var plan=access.plan(subject,permission,resource);
        if (!plan.tenantWide() || !subject.equals(plan.subject())) throw new Rejection(403,"INBOUND_METER_ACCESS_DENIED");
        return plan;
    }
    private GroundWeighingRepository.Task task(UUID tenant,UUID id,long version) {
        if(id==null || version<0) throw bad();
        var result=store.lockTask(tenant,id);
        if(result==null) throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
        if(result.version()!=version) throw reject("INBOUND_METER_VERSION_CONFLICT");
        if(result.noticeStatus()==null || !NOTICES.contains(result.noticeStatus())
                || result.meteringStrategy()==null || !SCALE.contains(result.meteringStrategy())
                || result.qualityStrategy()==null || !QUALITY.contains(result.qualityStrategy()))
            throw reject("INBOUND_METER_METHOD_DENIED");
        return result;
    }
    private GroundWeighingRepository.Meter grossMeter(UUID tenant,UUID id,long version) {
        var result=store.lockMeter(tenant,id);
        if(version<0) throw bad();
        if(result==null || result.version()!=version || !"GROSS_RECORDED".equals(result.status())
                || result.grossAt()==null || result.station()==null || result.grossKg()==null)
            throw reject("INBOUND_METER_VERSION_CONFLICT");
        return result;
    }
    private void advance(UUID tenant,UUID actor,UUID task,long version,GroundWeighingRepository.Advance next) {
        if(store.advance(tenant,actor,task,version,next)!=1) throw reject("INBOUND_METER_VERSION_CONFLICT");
    }
    private static void ticket(Ticket ticket,GroundWeighingRepository.Task task) {
        if (ticket==null || !identifier(ticket.station(),64) || !identifier(ticket.ticketNo(),96)
                || ticket.observedAt()==null || ticket.observedAt().getNano()%1000!=0
                || ticket.observedAt().isAfter(task.databaseNow())
                || (task.arrivedAt()!=null && ticket.observedAt().isBefore(task.arrivedAt())) || ticket.kg()==null
                || ticket.kg().signum()<=0 || ticket.kg().stripTrailingZeros().scale()>4
                || ticket.kg().compareTo(new BigDecimal("99999999999999.9999"))>0) throw bad();
    }
    private static boolean identifier(String value,int width) {
        return value!=null && value.length()<=width && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }
    private static Rejection bad() { return new Rejection(400,"INBOUND_SCALE_INPUT_INVALID"); }
    private static Rejection reject(String code) { return new Rejection(409,code); }
}
