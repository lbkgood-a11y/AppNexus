package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ConfirmSteelMeasurementUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase.Ticket;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.domain.inboundexecution.MeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.StandardMeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.UnloadingFact;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.UUID;

/** 在固定钢铁模板下确认件数、理计和地磅差重。 */
public final class ConfirmSteelMeasurementService implements ConfirmSteelMeasurementUseCase {
    private final AccessPlanning access;
    private final SteelMeasurementRepository repository;

    /** @param access 公共权限 @param repository 钢铁计量仓储 */
    public ConfirmSteelMeasurementService(AccessPlanning access,SteelMeasurementRepository repository) {
        this.access=access;this.repository=repository;
    }

    /** {@inheritDoc} */
    @Override public SteelMeasurement confirm(VerifiedTenantContext context,Command input) {
        if(input==null||input.taskId()==null||input.taskVersion()<0||input.basis()==null||input.packages()<1
                ||!key(input.meteringNo(),64)||input.theoreticalKg()==null
                ||(input.gross()==null)!=(input.tare()==null)||(input.basis()==Basis.SCALE && input.gross()==null)) throw invalid();
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var plan=access.plan(subject,PERMISSION,RegisterExternalMeasurementUseCase.RESOURCE);
        if(!plan.tenantWide()||!subject.equals(plan.subject())) throw new Rejection(403,"INBOUND_METER_ACCESS_DENIED");
        var tenant=context.tenantId();var actor=context.accountId();
        return repository.transaction(plan,()->{
            var task=repository.lock(tenant,input.taskId());
            if(task==null) throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
            if(task.version()!=input.taskVersion()) throw conflict();
            if(!"WORKING".equals(task.status())||task.inspectionId()==null||task.maxVariance()==null
                    ||task.maxVariance().signum()<0||task.maxVariance().compareTo(BigDecimal.ONE)>0)
                throw new Rejection(409,"INBOUND_STEEL_METER_GATE_REQUIRED");
            if(input.gross()!=null) {
                ticket(input.gross(),task);
                ticket(input.tare(),task);
                if(!input.gross().station().equalsIgnoreCase(input.tare().station())
                        ||!input.tare().observedAt().isAfter(input.gross().observedAt())) throw invalid();
            }
            var method=input.basis()==Basis.SCALE?MeteringStrategy.Method.SCALE_DIFFERENCE:MeteringStrategy.Method.THEORETICAL;
            var id=UUID.randomUUID();
            MeteringStrategy.Result result;
            try {
                var evidence=new MeteringStrategy.Evidence(id,method,input.gross()==null?null:input.gross().kg(),
                        input.tare()==null?null:input.tare().kg(),null,input.theoreticalKg(),input.packages());
                result=new StandardMeteringStrategy().evaluate(evidence,
                        new MeteringStrategy.Rule(method,task.bothRequired(),task.maxVariance()),
                        new UnloadingFact(UnloadingFact.Disposition.NOT_REQUIRED,null,BigDecimal.ZERO)).orElseThrow();
            } catch(IllegalArgumentException e) {
                throw new Rejection(400,"INBOUND_STEEL_MEASUREMENT_INVALID_OR_VARIANCE_EXCEEDED");
            }
            var physical=result.physicalNetKg().orElse(null);
            var ratio=result.varianceRatio().orElse(null);
            repository.insert(tenant,actor,new SteelMeasurementRepository.Measurement(id,input.taskId(),task.inspectionId(),
                    input.meteringNo(),method.name(),physical,input.theoreticalKg(),ratio,input.packages(),input.gross(),input.tare()));
            if(repository.advance(tenant,actor,input.taskId(),task.version())!=1) throw conflict();
            return new SteelMeasurement(id,0,task.version()+1,decimal(physical),
                    input.theoreticalKg().setScale(4).toPlainString(),decimal(ratio),input.packages());
        });
    }

    private static void ticket(Ticket t,SteelMeasurementRepository.Task task) {
        if(!key(t.station(),64)||!key(t.ticketNo(),96)||t.kg()==null||t.observedAt()==null
                ||t.observedAt().getNano()%1000!=0||t.observedAt().isAfter(task.now())
                ||(task.arrivedAt()!=null && t.observedAt().isBefore(task.arrivedAt()))) throw invalid();
    }
    private static boolean key(String s,int limit) {
        return s!=null&&s.length()<=limit&&s.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }
    private static String decimal(BigDecimal v) { return v==null?null:v.toPlainString(); }
    private static Rejection invalid() { return new Rejection(400,"INBOUND_STEEL_MEASUREMENT_INPUT_INVALID"); }
    private static Rejection conflict() { return new Rejection(409,"INBOUND_METER_VERSION_CONFLICT"); }
}
