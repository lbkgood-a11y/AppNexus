package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Set;
import java.util.UUID;

/** 只对有效入库作业登记样品，作业锁、当前质检切换和行版本在同一事务。 */
public final class RegisterInboundSampleService implements RegisterInboundSampleUseCase, DataAccessExecutor {
    private static final Set<String> TASK_STATES = Set.of("ARRIVED", "WORKING", "READY_TO_RECEIVE", "CORRECTION_REQUIRED");
    private static final Set<String> NOTICE_STATES = Set.of("ISSUED", "RECEIVING");
    private final AccessPlanning access;
    private final InboundQualityOrderRepository repository;

    /** @param access 统一权限计划 @param repository 质检样品事务和存储端口 */
    public RegisterInboundSampleService(AccessPlanning access, InboundQualityOrderRepository repository) {
        this.access = access;
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override public Set<String> enforcedResources() {
        return Set.of(RESOURCE);
    }

    /** {@inheritDoc} */
    @Override public Registered register(VerifiedTenantContext context, Command command) {
        if (command == null || command.taskId() == null || command.taskVersion() < 0
                || (command.priorQualityId() == null) != (command.priorRowVersion() == null)
                || (command.priorRowVersion() != null && command.priorRowVersion() < 0)
                || !identifier(command.qualityNo()) || !identifier(command.barcode())) {
            throw new Rejection(400, "INBOUND_SAMPLE_INPUT_INVALID");
        }
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var plan = access.plan(subject, PERMISSION, RESOURCE);
        if (!plan.tenantWide() || !subject.equals(plan.subject())) {
            throw new Rejection(403, "INBOUND_SAMPLE_ACCESS_DENIED");
        }

        var tenant = context.tenantId();
        var actor = context.accountId();
        var taskId = command.taskId();
        return repository.transaction(plan, () -> {
            var task = repository.lockTask(tenant, taskId);
            if (task == null) throw new Rejection(404, "INBOUND_TASK_NOT_FOUND");
            if (task.rowVersion() != command.taskVersion()) throw conflict();
            if (!TASK_STATES.contains(task.status()) || !NOTICE_STATES.contains(task.noticeStatus())) {
                throw new Rejection(409, "INBOUND_SAMPLE_STATE_CONFLICT");
            }
            var active = repository.lockActive(tenant, taskId);
            if ((active == null) != (command.priorQualityId() == null)) throw conflict();
            int version = 1;
            if (active != null) {
                if (!active.id().equals(command.priorQualityId()) || active.rowVersion() != command.priorRowVersion()) {
                    throw conflict();
                }
                if (!"DECIDED".equals(active.status())) {
                    throw new Rejection(409, "INBOUND_SAMPLE_STATE_CONFLICT");
                }
                if (repository.deactivate(tenant, actor, taskId, active) != 1) throw conflict();
                version = Math.incrementExact(active.qualityVersion());
            }
            var id = UUID.randomUUID();
            repository.insert(tenant, actor, new InboundQualityOrderRepository.Sample(id, taskId, version,
                    command.qualityNo(), command.barcode()));
            if (repository.advanceTask(tenant, actor, taskId, task.rowVersion()) != 1) throw conflict();
            return new Registered(id, version, task.rowVersion() + 1);
        });
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    }

    private static Rejection conflict() {
        return new Rejection(409, "INBOUND_SAMPLE_VERSION_CONFLICT");
    }
}
