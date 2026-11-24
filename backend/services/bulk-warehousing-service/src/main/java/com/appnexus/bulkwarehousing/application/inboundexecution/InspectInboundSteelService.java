package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.InspectInboundSteelUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Set;
import java.util.UUID;

/** 以作业固定模板和已授权附件为依据，在统一作业锁内出具钢铁首次核验结果。 */
public final class InspectInboundSteelService implements InspectInboundSteelUseCase {
    private static final Set<String> TASK_STATES = Set.of("ARRIVED", "WORKING");
    private static final Set<String> NOTICE_STATES = Set.of("ISSUED", "RECEIVING");
    private final AccessPlanning access;
    private final InboundQualityOrderRepository repository;
    private final SteelInspectionEvidence attachments;

    /** @param access 统一操作计划 @param repository 质检仓储 @param attachments 平台附件出站端口 */
    public InspectInboundSteelService(AccessPlanning access, InboundQualityOrderRepository repository,
                                      SteelInspectionEvidence attachments) {
        this.access=access;
        this.repository=repository;
        this.attachments=attachments;
    }

    /** {@inheritDoc} */
    @Override public SteelInspection inspect(VerifiedTenantContext context, Command input) {
        if (input==null || input.taskId()==null || input.taskVersion()<0 || input.certificateId()==null
                || input.appearanceId()==null || input.certificateId().equals(input.appearanceId())
                || !identifier(input.qualityNo()) || !identifier(input.heatNo()) || !identifier(input.certificateNo())) {
            throw new Rejection(400,"INBOUND_STEEL_INSPECTION_INPUT_INVALID");
        }
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var plan=access.plan(subject,PERMISSION,RegisterInboundSampleUseCase.RESOURCE);
        if (!plan.tenantWide() || !subject.equals(plan.subject())) throw new Rejection(403,"INBOUND_SAMPLE_ACCESS_DENIED");
        var tenant=context.tenantId(); var actor=context.accountId(); var taskId=input.taskId();
        return repository.transaction(plan,()->{
            var task=repository.lockTask(tenant,taskId);
            if(task==null) throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
            if(task.rowVersion()!=input.taskVersion()) throw conflict();
            if(!TASK_STATES.contains(task.status()) || task.noticeStatus()==null || !NOTICE_STATES.contains(task.noticeStatus())
                    || !repository.supportsSteelInspection(tenant,task.templateId())
                    || repository.lockActive(tenant,taskId)!=null) {
                throw new Rejection(409,"INBOUND_STEEL_INSPECTION_STATE_CONFLICT");
            }
            attachments.requireAvailable(context,taskId,input.certificateId(),input.appearanceId());
            String decision=input.certificateVerified() && input.appearanceVerified()?"PASS":"FAIL";
            var quality=UUID.randomUUID();
            repository.insertSteelInspection(tenant,actor,taskId,quality,input.qualityNo(),decision,
                    new InboundQualityOrderRepository.SteelEvidence(task.templateId(),input.heatNo(),input.certificateNo(),
                            input.certificateId(),input.appearanceId(),input.certificateVerified(),input.appearanceVerified()));
            if(repository.advanceAfterSteelInspection(tenant,actor,taskId,task.rowVersion(),decision)!=1) throw conflict();
            return new SteelInspection(quality,decision,task.rowVersion()+1);
        });
    }

    private static boolean identifier(String value) {
        return value!=null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}");
    }

    private static Rejection conflict() { return new Rejection(409,"INBOUND_STEEL_INSPECTION_VERSION_CONFLICT"); }
}
