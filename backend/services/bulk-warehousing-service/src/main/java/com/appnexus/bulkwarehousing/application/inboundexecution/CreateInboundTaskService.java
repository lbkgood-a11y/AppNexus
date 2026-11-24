package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 来源授权、模板固定及到场登记。存储层锁住通知，原三单用例仍锁车次和检查状态。 */
public final class CreateInboundTaskService implements CreateInboundTaskUseCase {
    private static final Set<String> OPEN = Set.of("ISSUED", "RECEIVING");
    private final AccessPlanning access;
    private final FieldProtection fields;
    private final InboundTaskRegistrationStore store;

    /** @param access 统一权限计划 @param store 事务仓储 */
    public CreateInboundTaskService(AccessPlanning access, FieldProtection fields, InboundTaskRegistrationStore store) {
        this.access = access;
        this.fields = fields;
        this.store = store;
    }

    /** {@inheritDoc} */
    @Override public InboundArrivalContext context(VerifiedTenantContext subject, UUID noticeId) {
        var plan = require(subject);
        return store.transaction(plan, () -> {
            var tenant = subject.tenantId();
            var notice = notice(tenant, noticeId, false);
            UUID fixed = fixedTemplate(tenant, noticeId, notice.templateId());
            boolean open = OPEN.contains(notice.status());
            var selection = selection(tenant, noticeId, fixed, open);
            return new InboundArrivalContext(open, selection.template(), selection.templates(), store.tasks(tenant, noticeId),
                    allowedIdentities(plan));
        });
    }

    /** {@inheritDoc} */
    @Override public InboundArrivalTask create(VerifiedTenantContext subject, UUID noticeId, InboundArrivalCommand input) {
        var plan = require(subject);
        var command = normalized(input);
        if (!allowedIdentities(plan).contains(command.identity())) {
            throw new Rejection(403, "INBOUND_TASK_VEHICLE_FIELD_FORBIDDEN");
        }
        return store.transaction(plan, () -> {
            var tenant = subject.tenantId();
            var notice = notice(tenant, noticeId, true);
            var prior = store.existing(tenant, command.requestId());
            if (prior != null) {
                if (prior.noticeId().equals(noticeId) && prior.identity().equals(command.identity().name())
                        && Objects.equals(prior.identifier(), command.identifier())
                        && (command.templateId() == null || prior.templateId().equals(command.templateId()))) {
                    return prior.task();
                }
                throw new Rejection(409, "INBOUND_TASK_REQUEST_CONFLICT");
            }
            if (!OPEN.contains(notice.status())) throw new Rejection(409, "INBOUND_TASK_NOTICE_CLOSED");
            UUID fixed = fixedTemplate(tenant, noticeId, notice.templateId());
            var selection = selection(tenant, noticeId, fixed, true);
            var configured = selection.template();
            if (configured != null && command.templateId() != null && !configured.id().equals(command.templateId())) {
                throw new Rejection(409, "INBOUND_TASK_TEMPLATE_CHANGED");
            }
            if (configured == null && selection.templates().stream().noneMatch(item -> item.id().equals(command.templateId()))) {
                throw new Rejection(409, "INBOUND_TASK_TEMPLATE_REQUIRED");
            }
            var template = template(tenant, configured == null ? command.templateId() : configured.id());
            if (notice.templateId() == null) store.bindTemplate(tenant, noticeId, template.id(), subject.accountId());
            String stage = switch (template.qualityStrategy()) {
                case "SAMPLED", "QUALIFIED_AND_UNLOADED", "DOCUMENT_AND_APPEARANCE" -> "WAIT_QUALITY";
                default -> throw new Rejection(409, "INBOUND_TASK_TEMPLATE_UNAVAILABLE");
            };
            var id = command.requestId();
            var task = new InboundArrivalTask(id, "T-" + id.toString().replace("-", ""), "WORKING", stage);
            store.insert(tenant, noticeId, template.id(), subject.accountId(), command, task);
            return task;
        });
    }

    private UUID fixedTemplate(UUID tenant, UUID notice, UUID fixed) {
        if (fixed != null) return fixed;
        var previous = store.previousTemplates(tenant, notice);
        if (previous.size() > 1) throw new Rejection(409, "INBOUND_TASK_TEMPLATE_AMBIGUOUS");
        return previous.isEmpty() ? null : previous.getFirst();
    }

    private record Selection(InboundArrivalTemplate template, List<InboundArrivalTemplate> templates) {}

    private Selection selection(UUID tenant, UUID notice, UUID fixed, boolean open) {
        if (fixed != null) return new Selection(template(tenant, fixed), List.of());
        if (!open) return new Selection(null, List.of());
        var matching=store.matchingTemplates(tenant, notice);
        if (matching.size()==1) return new Selection(matching.getFirst(), List.of());
        return new Selection(null, matching.isEmpty() ? store.templates(tenant,null) : matching);
    }

    private InboundArrivalTemplate template(UUID tenant, UUID id) {
        if (id == null) throw new Rejection(409, "INBOUND_TASK_TEMPLATE_REQUIRED");
        var templates = store.templates(tenant, id);
        if (templates.isEmpty()) throw new Rejection(409, "INBOUND_TASK_TEMPLATE_UNAVAILABLE");
        return templates.getFirst();
    }

    private InboundTaskRegistrationStore.Notice notice(UUID tenant, UUID id, boolean lock) {
        var notice = store.notice(tenant, id, lock);
        if (notice == null) throw new Rejection(404, "INBOUND_TASK_NOTICE_NOT_FOUND");
        return notice;
    }

    private AccessPlan require(VerifiedTenantContext context) {
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var source = access.plan(subject, WarehouseBalances.READ_PERMISSION, WarehouseBalances.RESOURCE);
        var plan = access.plan(subject, PERMISSION, ReadInboundWorkbenchUseCase.RESOURCE);
        if (!source.tenantWide() || !plan.tenantWide() || !subject.equals(plan.subject()) || !subject.equals(source.subject())
                || !PERMISSION.equals(plan.permissionCode()) || !WarehouseBalances.READ_PERMISSION.equals(source.permissionCode())) {
            throw new Rejection(403, "INBOUND_TASK_ACCESS_DENIED");
        }
        return plan;
    }

    private List<VehicleIdentity> allowedIdentities(AccessPlan plan) {
        var restrictions = fields.restrictions(plan.subject(), ReadInboundWorkbenchUseCase.RESOURCE);
        var allowed = new java.util.ArrayList<VehicleIdentity>();
        // 任一字段限制均不允许登记对应身份，避免替代请求侧绕过公共字段策略。
        if (!restrictions.containsKey("vehiclePlate")) allowed.add(VehicleIdentity.PLATED);
        if (!restrictions.containsKey("temporaryVehicleNo")) allowed.add(VehicleIdentity.UNPLATED);
        allowed.add(VehicleIdentity.OTHER);
        return List.copyOf(allowed);
    }

    private static InboundArrivalCommand normalized(InboundArrivalCommand command) {
        if (command == null || command.requestId() == null || command.identity() == null) {
            throw new Rejection(400, "INBOUND_TASK_INPUT_INVALID");
        }
        String value = command.identifier() == null ? "" : command.identifier().strip();
        int max = command.identity() == VehicleIdentity.PLATED ? 32 : 64;
        boolean other = command.identity() == VehicleIdentity.OTHER;
        if (value.length() > max || other != value.isEmpty() || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new Rejection(400, "INBOUND_TASK_INPUT_INVALID");
        }
        return new InboundArrivalCommand(command.requestId(), command.templateId(), command.identity(), other ? null : value);
    }
}
