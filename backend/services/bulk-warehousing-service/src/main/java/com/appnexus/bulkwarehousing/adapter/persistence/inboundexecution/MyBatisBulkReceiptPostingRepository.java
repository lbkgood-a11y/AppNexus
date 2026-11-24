package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.application.inboundexecution.BulkReceiptPostingRepository;
import com.appnexus.bulkwarehousing.application.inboundexecution.BulkReceiptReversalRepository;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReceiptExecutionRepository;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.nio.ByteBuffer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 实际库存过账、作业锁及幂等回执共用一个数据源和事务。 */
public final class MyBatisBulkReceiptPostingRepository implements ReceiptExecutionRepository,
        BulkReceiptPostingRepository, BulkReceiptReversalRepository {
    private final ReceiptPostingMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /** @param mapper 固定仓储 SQL @param gate 公共事务操作许可 @param manager 组合应用事务管理器 */
    public MyBatisBulkReceiptPostingRepository(ReceiptPostingMapper mapper, ScopedWriteGate gate,
                                               PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.gate = gate;
        this.transaction = new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public <T> T transaction(Supplier<T> work) {
        try {
            return transaction.execute(status -> work.get());
        } catch (DuplicateKeyException e) {
            throw new Rejection(409, "INBOUND_RECEIPT_POST_DUPLICATE");
        }
    }

    /** {@inheritDoc} */
    @Override public Optional<LockedReceipt> lock(AccessPlan plan, UUID taskId, UUID receiptId) {
        if (!plan.tenantWide() || !(ReceiptPostingMapper.POST.equals(plan.permissionCode())
                || ReceiptPostingMapper.REVERSE.equals(plan.permissionCode()))) return Optional.empty();
        gate.authorizeOperation(plan);
        var key = bytes(plan.subject().tenantId());
        var task = mapper.lockTask(key, taskId.toString());
        if (task == null) return Optional.empty();
        var row = mapper.lockReceipt(key, taskId.toString(), receiptId.toString());
        return row == null ? Optional.empty() : Optional.of(new LockedReceipt(task, row.rowVersion(),
                State.valueOf(row.status())));
    }

    /** {@inheritDoc} */
    @Override public Optional<Execution> findExecution(VerifiedTenantContext context, UUID taskId, Action action, String key) {
        var tenant = bytes(context.tenantId());
        var row = switch (action) {
            case POST -> mapper.findExecution(tenant, taskId.toString(), key);
            case REVERSE -> mapper.findReversal(tenant, taskId.toString(), key);
        };
        return row == null ? Optional.empty() : Optional.of(new Execution(UUID.fromString(row.receiptId()),
                row.digest(), row.resultingVersion()));
    }

    /** {@inheritDoc} */
    @Override public void insertExecution(VerifiedTenantContext context, UUID taskId, Action action, String key,
                                          Execution execution) {
        var tenant = bytes(context.tenantId());
        var actor = context.accountId().toString();
        var task = taskId.toString();
        var receipt = execution.receiptId().toString();
        switch (action) {
            case POST -> mapper.insertExecution(tenant, actor, task, receipt, key, execution.digest(), execution.resultingVersion());
            case REVERSE -> mapper.insertReversal(tenant, actor, task, receipt, key, execution.digest(), execution.resultingVersion());
        }
    }

    /** {@inheritDoc} */
    @Override public List<StockInput> lockPostableDraft(UUID tenant, UUID task, UUID receipt) {
        var rows = mapper.lockDraftLines(bytes(tenant), task.toString(), receipt.toString());
        if(rows.isEmpty() || rows.size()!=rows.getFirst().expectedLines() || rows.size()!=rows.getFirst().actualLines()
                || rows.stream().map(ReceiptPostingMapper.DraftLineRow::lineKg).reduce(BigDecimal.ZERO,BigDecimal::add)
                    .compareTo(rows.getFirst().headerKg())!=0) return List.of();
        return rows.stream().map(this::stockInput).toList();
    }

    private StockInput stockInput(ReceiptPostingMapper.DraftLineRow row) {
        var item = new InboundReceiptReferences.Item(row.productId(),
                InboundReceiptReferences.IdentityKind.valueOf(row.identityKind()), row.identityId(), row.productVersion());
        try {
            var snapshot = json.readTree(row.snapshot());
            var position = json.treeToValue(snapshot.path("position"), InboundReceiptReferences.Position.class);
            var storedItem = json.treeToValue(snapshot.path("item"), InboundReceiptReferences.Item.class);
            var quantity = snapshot.path("quantity");
            if (position == null || !row.warehouseId().equals(position.warehouseId())
                    || !row.locationId().equals(position.locationId()) || !item.equals(storedItem)
                    || !row.ownerId().equals(snapshot.path("ownerPartnerId").asText())
                    || !row.unitId().equals(quantity.path("baseUnitId").asText())
                    || !quantity.path("baseQuantity").isNumber()
                    || row.quantity().compareTo(quantity.path("baseQuantity").decimalValue()) != 0
                    || !quantity.path("sourceQuantity").isNumber()
                    || row.lineKg().compareTo(quantity.path("sourceQuantity").decimalValue()) != 0) {
                throw new Rejection(409, "INBOUND_RECEIPT_DRAFT_SNAPSHOT_CONFLICT");
            }
            return new StockInput(UUID.fromString(row.lineId()), UUID.fromString(row.noticeId()),
                    UUID.fromString(row.noticeLineId()), row.ownerId(), position, item, row.unitId(),
                    row.batch(), row.quantity(), row.qualityState(), row.qualityVersion(), row.qualitySnapshot(),row.lpn());
        } catch (JacksonException e) {
            throw new Rejection(409, "INBOUND_RECEIPT_DRAFT_SNAPSHOT_CONFLICT");
        }
    }

    /** {@inheritDoc} */
    @Override public int consumeNoticeLine(UUID tenant, UUID actor, StockInput input) {
        return mapper.consumeLine(bytes(tenant), actor.toString(), input.noticeId().toString(),
                input.noticeLineId().toString(), input.quantity());
    }

    /** {@inheritDoc} */
    @Override public int advanceNotice(UUID tenant, UUID actor, StockInput input) {
        return mapper.advanceNotice(bytes(tenant), actor.toString(), input.noticeId().toString(), input.quantity());
    }

    /** {@inheritDoc} */
    @Override public int advanceMultiLineNotice(UUID tenant,UUID actor,UUID notice) {
        return mapper.advanceMultiLineNotice(bytes(tenant),actor.toString(),notice.toString());
    }

    /** {@inheritDoc} */
    @Override public void insertStock(UUID tenant, UUID actor, StockInput input) {
        var position = input.position();
        var item = input.item();
        if(input.lpn()!=null&&mapper.activeLpnExists(bytes(tenant),position.warehouseId(),input.lpn())!=null)
            throw new Rejection(409,"INBOUND_STEEL_LPN_ALREADY_IN_STOCK");
        mapper.insertStock(bytes(tenant), actor.toString(), new ReceiptPostingMapper.StockRow(input.receiptLineId().toString(),
                position.warehouseId(), position.areaId(), position.locationId(), position.warehouseVersion(),
                position.areaVersion(), position.locationVersion(), input.ownerId(), item.productId(), item.kind().name(),
                item.identityId(), input.baseUnitId(), input.batch(), input.quantity(),
                input.qualityState(), input.qualityVersion(), input.qualitySnapshot(),input.lpn()));
    }

    /** {@inheritDoc} */
    @Override public int markReceiptPosted(UUID tenant, UUID actor, UUID task, UUID receipt, long version, String qualityState) {
        return mapper.markReceiptPosted(bytes(tenant), actor.toString(), task.toString(), receipt.toString(), version, qualityState);
    }

    /** {@inheritDoc} */
    @Override public int markTaskReceived(UUID tenant, UUID actor, UUID task, long version) {
        return mapper.markTaskReceived(bytes(tenant), actor.toString(), task.toString(), version);
    }

    /** {@inheritDoc} */
    @Override public ReversalInput lockReversibleStock(UUID tenant, UUID task, UUID receipt) {
        var rows = mapper.lockReversibleLines(bytes(tenant), task.toString(), receipt.toString());
        if (rows.size() != 1) return null;
        var row = rows.getFirst();
        return new ReversalInput(UUID.fromString(row.stockId()), UUID.fromString(row.noticeId()),
                UUID.fromString(row.noticeLineId()), row.quantity());
    }

    /** {@inheritDoc} */
    @Override public int reverseStock(UUID tenant, UUID actor, ReversalInput input) {
        return mapper.reverseStock(bytes(tenant), actor.toString(), input.stockId().toString(), input.quantity());
    }

    /** {@inheritDoc} */
    @Override public int restoreNoticeLine(UUID tenant, UUID actor, ReversalInput input) {
        return mapper.restoreLine(bytes(tenant), actor.toString(), input.noticeId().toString(),
                input.noticeLineId().toString(), input.quantity());
    }

    /** {@inheritDoc} */
    @Override public int restoreNotice(UUID tenant, UUID actor, ReversalInput input) {
        return mapper.restoreNotice(bytes(tenant), actor.toString(), input.noticeId().toString(), input.quantity());
    }

    /** {@inheritDoc} */
    @Override public int markReceiptReversed(UUID tenant, UUID actor, UUID task, UUID receipt, long version) {
        return mapper.markReceiptReversed(bytes(tenant), actor.toString(), task.toString(), receipt.toString(), version);
    }

    /** {@inheritDoc} */
    @Override public int markTaskCorrectionRequired(UUID tenant, UUID actor, UUID task, long version) {
        return mapper.markTaskCorrectionRequired(bytes(tenant), actor.toString(), task.toString(), version);
    }

    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
