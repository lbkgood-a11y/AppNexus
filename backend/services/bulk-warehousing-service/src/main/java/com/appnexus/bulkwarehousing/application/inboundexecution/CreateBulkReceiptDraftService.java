package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.application.InboundReferenceSource;
import com.appnexus.bulkwarehousing.domain.inboundexecution.QualityGateStrategy;
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

/** 将已确认的物理净重按授权位置、品规及单位事实定为散货草稿。 */
public final class CreateBulkReceiptDraftService implements CreateBulkReceiptDraftUseCase, DataAccessExecutor {
    private static final Set<String> NOTICE_STATES = Set.of("ISSUED", "RECEIVING");
    private final AccessPlanning access;
    private final InboundReceiptOrderRepository repository;
    private final InboundReceiptReferences references;
    private final InboundReferenceSource source;

    /** @param access 统一授权 @param repository 仓储事务 @param references 授权引用核验 @param source 千克单位验证 */
    public CreateBulkReceiptDraftService(AccessPlanning access, InboundReceiptOrderRepository repository,
                                        InboundReceiptReferences references, InboundReferenceSource source) {
        this.access = access;
        this.repository = repository;
        this.references = references;
        this.source = source;
    }

    /** {@inheritDoc} */
    @Override public Set<String> enforcedResources() { return Set.of(RESOURCE); }

    /** {@inheritDoc} */
    @Override public BulkReceiptDraft create(VerifiedTenantContext context, Command command) {
        if (command == null || command.taskId() == null || command.noticeLineId() == null || command.position() == null
                || command.kilogramUnitId() == null || command.taskVersion() < 0 || command.meteringVersion() < 0
                || !identifier(command.receiptNo()) || !identifier(command.batchNo())
                || (command.conversionRuleId() == null) != (command.conversionRuleVersion() == null)
                || (command.conversionRuleVersion() != null && command.conversionRuleVersion() < 0)) {
            throw new Rejection(400, "INBOUND_RECEIPT_DRAFT_INPUT_INVALID");
        }
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var plan = access.plan(subject, PERMISSION, RESOURCE);
        if (!plan.tenantWide() || !subject.equals(plan.subject())) throw new Rejection(403, "INBOUND_RECEIPT_ACCESS_DENIED");
        var tenant = context.tenantId();
        var actor = context.accountId();
        return repository.transaction(plan, () -> {
            var task = repository.lockTask(tenant, command.taskId());
            if (task == null) throw new Rejection(404, "INBOUND_TASK_NOT_FOUND");
            if (task.rowVersion() != command.taskVersion()) throw conflict();
            boolean correction = "CORRECTION_REQUIRED".equals(task.status());
            if (!task.bulkMeteringEnabled() || !("READY_TO_RECEIVE".equals(task.status()) || correction)
                    || task.noticeStatus() == null || !NOTICE_STATES.contains(task.noticeStatus())) {
                throw new Rejection(409, "INBOUND_RECEIPT_DRAFT_STATE_CONFLICT");
            }
            if (correction ? command.replacesReceiptId() == null
                    || !command.replacesReceiptId().equals(repository.lockLatestReversed(tenant, command.taskId()))
                    : command.replacesReceiptId() != null) {
                throw new Rejection(409, "INBOUND_RECEIPT_REOPEN_SOURCE_CONFLICT");
            }
            if (!task.warehouseId().toString().equals(command.position().warehouseId())) {
                throw new Rejection(400, "INBOUND_RECEIPT_WAREHOUSE_MISMATCH");
            }
            var line = repository.lockNoticeLine(tenant, task.noticeId(), command.noticeLineId());
            if (line == null) throw new Rejection(404, "INBOUND_NOTICE_LINE_NOT_FOUND");
            var sample = Optional.ofNullable(repository.lockSample(tenant, command.taskId()))
                    .map(value -> new QualityGateStrategy.QualityFact(value.id(), value.version(), value.sampled(),
                            "PASS".equals(value.decision()), "FAIL".equals(value.decision()), false, false));
            var gate = new TemplateQualityGate(TemplateQualityGate.Mode.SAMPLED).evaluate(
                    QualityGateStrategy.Point.CREATE_RECEIPT, sample,
                    new UnloadingFact(UnloadingFact.Disposition.NOT_REQUIRED, null, BigDecimal.ZERO));
            if (gate.outcome() == QualityGateStrategy.Outcome.BLOCK) throw new Rejection(409, gate.reasonCode());
            var meter = repository.lockMeter(tenant, command.taskId());
            if (meter == null || meter.rowVersion() != command.meteringVersion()) throw conflict();
            var resolved = resolve(context, command, task, line, meter.physicalNetKg());
            String qualityState = gate.outcome() == QualityGateStrategy.Outcome.ACCEPT_PENDING_LAB ? "PENDING_LAB" : "QUALIFIED";
            var opened = repository.lockActiveDraft(tenant, command.taskId());
            if (opened != null && (!opened.collecting() || !opened.number().equals(command.receiptNo()))) throw conflict();
            var id = opened == null ? UUID.randomUUID() : opened.id();
            repository.insertDraft(tenant, actor, new InboundReceiptOrderRepository.Draft(id, UUID.randomUUID(),
                    command.taskId(), task.noticeId(), command.noticeLineId(), meter.id(),
                    gate.qualityId().orElseThrow(), command.receiptNo(), command.batchNo(), qualityState,
                    meter.physicalNetKg(), resolved, command.replacesReceiptId(), meter.method()),opened != null);
            if (repository.advanceTask(tenant, actor, command.taskId(), task.rowVersion()) != 1) throw conflict();
            return new BulkReceiptDraft(id, opened == null ? 0 : opened.version()+1, task.rowVersion() + 1, qualityState);
        });
    }

    private InboundReceiptReferences.Resolved resolve(VerifiedTenantContext context, Command command,
            InboundReceiptOrderRepository.Task task, InboundReceiptOrderRepository.NoticeLine line, BigDecimal netKg) {
        var kilogram = command.kilogramUnitId().toString();
        source.requireMassUnit(context, kilogram, "KILOGRAM");
        InboundReceiptReferences.Conversion conversion = command.conversionRuleId() == null
                ? new InboundReceiptReferences.SameUnit()
                : new InboundReceiptReferences.UnitRule(command.conversionRuleId().toString(), command.conversionRuleVersion());
        var resolved = references.resolve(context, new InboundReceiptReferences.Request(command.position(), line.item(),
                task.ownerId().toString(), kilogram, netKg.toPlainString(), conversion));
        var evidence = resolved.quantity();
        var quantity = evidence.baseQuantity();
        if (!line.baseUnitId().toString().equals(evidence.baseUnitId()) || quantity.signum() <= 0
                || quantity.stripTrailingZeros().scale() > 12 || quantity.compareTo(line.remaining()) > 0) {
            throw new Rejection(409, "INBOUND_RECEIPT_NOTICE_LIMIT_OR_UNIT_CONFLICT");
        }
        return resolved;
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    }

    private static Rejection conflict() { return new Rejection(409, "INBOUND_RECEIPT_DRAFT_VERSION_CONFLICT"); }
}
