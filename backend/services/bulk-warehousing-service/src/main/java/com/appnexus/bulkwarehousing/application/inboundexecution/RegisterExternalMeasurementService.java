package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.domain.inboundexecution.QualityGateStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.MeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.StandardMeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.TemplateQualityGate;
import com.appnexus.bulkwarehousing.domain.inboundexecution.UnloadingFact;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 登记支持外部凭证与采样暂估的作业，保留账前物理净重。 */
public final class RegisterExternalMeasurementService implements RegisterExternalMeasurementUseCase, DataAccessExecutor {
    private static final Set<String> EXTERNAL_STRATEGIES = Set.of("EXTERNAL_NET", "SCALE_OR_EXTERNAL");
    private static final Set<String> NOTICE_STATES = Set.of("ISSUED", "RECEIVING");
    private final AccessPlanning access;
    private final InboundMeteringOrderRepository repository;

    /** @param access 统一授权 @param repository 计量事务端口 */
    public RegisterExternalMeasurementService(AccessPlanning access, InboundMeteringOrderRepository repository) {
        this.access = access;
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override public Set<String> enforcedResources() { return Set.of(RESOURCE); }

    /** {@inheritDoc} */
    @Override public ExternalMeasurementRegistration registerExternal(VerifiedTenantContext context, Command command) {
        if (command == null || command.taskId() == null || command.taskVersion() < 0 || command.source() == null
                || command.netKg() == null || !identifier(command.meteringNo(), 64)
                || !identifier(command.externalReference(), 96)) throw new Rejection(400, "INBOUND_METER_INPUT_INVALID");
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var plan = access.plan(subject, PERMISSION, RESOURCE);
        if (!plan.tenantWide() || !subject.equals(plan.subject())) throw new Rejection(403, "INBOUND_METER_ACCESS_DENIED");
        var id = UUID.randomUUID();
        var unloading = new UnloadingFact(UnloadingFact.Disposition.NOT_REQUIRED, null, BigDecimal.ZERO);
        var method = MeteringStrategy.Method.EXTERNAL_NET;
        BigDecimal net;
        try {
            var evidence = new MeteringStrategy.Evidence(id, method, null, null, command.netKg(), null, null);
            net = new StandardMeteringStrategy().evaluate(evidence, new MeteringStrategy.Rule(method, false, null),
                    unloading).orElseThrow().postingBasisKg();
        } catch (IllegalArgumentException e) {
            throw new Rejection(400, "INBOUND_METER_INPUT_INVALID");
        }
        var tenant = context.tenantId();
        var actor = context.accountId();
        return repository.transaction(plan, () -> {
            var task = repository.lockTask(tenant, command.taskId());
            if (task == null) throw new Rejection(404, "INBOUND_TASK_NOT_FOUND");
            if (task.version() != command.taskVersion()) throw new Rejection(409, "INBOUND_METER_VERSION_CONFLICT");
            if (!"WORKING".equals(task.status()) || task.noticeStatus() == null
                    || !NOTICE_STATES.contains(task.noticeStatus())) throw new Rejection(409, "INBOUND_METER_STATE_CONFLICT");
            if (task.meteringStrategy() == null || !EXTERNAL_STRATEGIES.contains(task.meteringStrategy())
                    || !"SAMPLED".equals(task.qualityStrategy())) throw new Rejection(409, "INBOUND_METER_METHOD_DENIED");
            var sample = repository.lockSample(tenant, command.taskId());
            var quality = Optional.ofNullable(sample).map(value -> new QualityGateStrategy.QualityFact(
                    value.id(), value.version(), value.sampled(), "PASS".equals(value.decision()),
                    "FAIL".equals(value.decision()), false, false));
            var gate = new TemplateQualityGate(TemplateQualityGate.Mode.SAMPLED)
                    .evaluate(QualityGateStrategy.Point.CONFIRM_MEASUREMENT, quality, unloading);
            if (gate.outcome() == QualityGateStrategy.Outcome.BLOCK) throw new Rejection(409, gate.reasonCode());
            repository.insertExternal(tenant, actor, new InboundMeteringOrderRepository.Confirmation(id, command.taskId(),
                    gate.qualityId().orElseThrow(), command.meteringNo(), command.source(), command.externalReference(), net));
            if (repository.advanceTask(tenant, actor, command.taskId(), task.version()) != 1) {
                throw new Rejection(409, "INBOUND_METER_VERSION_CONFLICT");
            }
            return new ExternalMeasurementRegistration(id, 0, task.version() + 1, net.toPlainString());
        });
    }

    private static boolean identifier(String value, int width) {
        return value != null && value.length() <= width && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }
}
