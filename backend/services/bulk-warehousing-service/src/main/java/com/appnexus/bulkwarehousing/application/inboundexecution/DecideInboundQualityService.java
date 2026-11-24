package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.DecideInboundQualityUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.bulkwarehousing.domain.inboundexecution.LabQualityRules;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.data.TransactionalBusinessEvents;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Set;

/** 对当前样品应用已发布实验室规则，并在同一事务保存结论与作业版本。 */
public final class DecideInboundQualityService implements DecideInboundQualityUseCase {
    private static final Set<String> STATES = Set.of("ARRIVED", "WORKING", "READY_TO_RECEIVE", "CORRECTION_REQUIRED");
    private static final Set<String> NOTICES = Set.of("ISSUED", "RECEIVING");
    private static final Set<String> LAB_MODES = Set.of("SAMPLED", "QUALIFIED_AND_UNLOADED");
    private final AccessPlanning access;
    private final InboundQualityOrderRepository repository;
    private final TransactionalBusinessEvents events;

    /** @param access 公共权限 @param repository 质检事务和规则仓储 @param events 同事务质量事件 */
    public DecideInboundQualityService(AccessPlanning access, InboundQualityOrderRepository repository,
                                       TransactionalBusinessEvents events) {
        this.access = access;
        this.repository = repository;
        this.events = events;
    }

    /** {@inheritDoc} */
    @Override public Decided decide(VerifiedTenantContext context, Command command) {
        if (command == null || command.taskId() == null || command.qualityId() == null
                || command.taskVersion() < 0 || command.qualityRowVersion() < 0
                || command.metrics() == null || command.metrics().isEmpty() || command.metrics().size() > 32) {
            throw new Rejection(400, "INBOUND_LAB_INPUT_INVALID");
        }
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var plan = access.plan(subject, PERMISSION, RegisterInboundSampleUseCase.RESOURCE);
        if (!plan.tenantWide() || !subject.equals(plan.subject())) {
            throw new Rejection(403, "INBOUND_SAMPLE_ACCESS_DENIED");
        }

        var tenant = context.tenantId();
        var actor = context.accountId();
        return repository.transaction(plan, () -> {
            var task = repository.lockTask(tenant, command.taskId());
            if (task == null) throw new Rejection(404, "INBOUND_TASK_NOT_FOUND");
            if (task.rowVersion() != command.taskVersion()) throw conflict();
            boolean alreadyReceived = "RECEIVED".equals(task.status());
            if (!alreadyReceived && (!STATES.contains(task.status()) || !NOTICES.contains(task.noticeStatus()))) {
                throw new Rejection(409, "INBOUND_LAB_STATE_CONFLICT");
            }

            var quality = repository.lockActive(tenant, command.taskId());
            if (quality == null || !quality.id().equals(command.qualityId())
                    || quality.rowVersion() != command.qualityRowVersion()) {
                throw conflict();
            }
            if (!"REGISTERED".equals(quality.status())) {
                throw new Rejection(409, "INBOUND_LAB_STATE_CONFLICT");
            }

            var rules = repository.readLabTemplate(tenant, task.templateId());
            if (rules == null || !LAB_MODES.contains(rules.mode()) || (alreadyReceived
                    && (!"SAMPLED".equals(rules.mode())
                    || !repository.hasPendingPostedSample(tenant, command.taskId(), quality.id())))) {
                throw new Rejection(409, "INBOUND_LAB_TEMPLATE_UNAVAILABLE");
            }
            LabQualityRules.Decision decision;
            try {
                decision = LabQualityRules.decide(rules.metrics(), command.metrics());
            } catch (IllegalArgumentException e) {
                throw new Rejection(400, "INBOUND_LAB_METRICS_INVALID");
            } catch (IllegalStateException e) {
                throw new Rejection(409, "INBOUND_LAB_TEMPLATE_UNAVAILABLE");
            }
            var snapshot = new InboundQualityOrderRepository.LabDecision(task.templateId(), rules.version(),
                    decision.name(), command.metrics(), rules.metrics());
            if (repository.decide(tenant, actor, command.taskId(), quality, snapshot) != 1) {
                throw conflict();
            }
            if (alreadyReceived) {
                events.publish(tenant, TransactionalBusinessEvents.Type.INBOUND_QUALITY_DECIDED, quality.id());
                return new Decided(quality.id(), decision.name(), quality.rowVersion() + 1, task.rowVersion());
            }
            if (repository.advanceAfterDecision(tenant, actor, command.taskId(), task.rowVersion(),
                    decision == LabQualityRules.Decision.FAIL) != 1) throw conflict();
            return new Decided(quality.id(), decision.name(), quality.rowVersion() + 1, task.rowVersion() + 1);
        });
    }

    private static Rejection conflict() {
        return new Rejection(409, "INBOUND_LAB_VERSION_CONFLICT");
    }
}
