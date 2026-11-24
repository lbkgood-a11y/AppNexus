package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.application.inboundexecution.InboundReceiptOrderRepository;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 草稿持久化适配，定序锁、快照、审计和 SQL 许可均属于同一事务。 */
public final class MyBatisInboundReceiptOrderRepository implements InboundReceiptOrderRepository {
    private final InboundReceiptOrderMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();

    /** @param mapper 固定 SQL @param gate 统一许可 @param manager 组合应用事务管理器 */
    public MyBatisInboundReceiptOrderRepository(InboundReceiptOrderMapper mapper, ScopedWriteGate gate,
                                                PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.gate = gate;
        this.transaction = new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public <T> T transaction(AccessPlan plan, Supplier<T> work) {
        try {
            return transaction.execute(status -> {
                gate.authorizeOperation(plan);
                return work.get();
            });
        } catch (DuplicateKeyException e) {
            throw new Rejection(409, "INBOUND_RECEIPT_DRAFT_DUPLICATE");
        }
    }

    /** {@inheritDoc} */
    @Override public Task lockTask(UUID tenant, UUID task) {
        var key = bytes(tenant);
        var row = mapper.lockTask(key, task.toString());
        if (row == null) return null;
        var notice = mapper.lockNotice(key, row.noticeId());
        if (notice == null) return null;
        boolean eligible = !row.extraDeduction() && mapper.eligibleTemplate(key, row.templateId()) != null;
        return new Task(row.rowVersion(), row.status(), UUID.fromString(row.noticeId()),
                UUID.fromString(notice.warehouseId()), UUID.fromString(notice.ownerId()), notice.status(), eligible);
    }

    /** {@inheritDoc} */
    @Override public NoticeLine lockNoticeLine(UUID tenant, UUID notice, UUID line) {
        var row = mapper.lockNoticeLine(bytes(tenant), notice.toString(), line.toString());
        if (row == null) return null;
        var kind = InboundReceiptReferences.IdentityKind.valueOf(row.identityKind());
        return new NoticeLine(new InboundReceiptReferences.Item(row.productId(), kind, row.identityId(),
                row.productVersion()), UUID.fromString(row.baseUnitId()), row.remaining());
    }

    /** {@inheritDoc} */
    @Override public Sample lockSample(UUID tenant, UUID task) {
        var row = mapper.lockSample(bytes(tenant), task.toString());
        return row == null ? null : new Sample(UUID.fromString(row.id()), row.version(), row.sampled(), row.decision());
    }

    /** {@inheritDoc} */
    @Override public Meter lockMeter(UUID tenant, UUID task) {
        var row = mapper.lockMeter(bytes(tenant), task.toString());
        return row == null || row.physicalNetKg() == null ? null
                : new Meter(UUID.fromString(row.id()), row.rowVersion(), row.physicalNetKg(), row.method());
    }

    /** {@inheritDoc} */
    @Override public UUID lockLatestReversed(UUID tenant, UUID task) {
        String id = mapper.lockLatestReversed(bytes(tenant), task.toString());
        return id == null ? null : UUID.fromString(id);
    }

    /** {@inheritDoc} */
    @Override public ActiveDraft lockActiveDraft(UUID tenant,UUID task) {
        var row=mapper.lockActiveDraft(bytes(tenant),task.toString());
        return row==null ? null : new ActiveDraft(UUID.fromString(row.id()),row.number(),row.version(),row.collecting());
    }

    /** {@inheritDoc} */
    @Override public void openDraft(UUID tenant,UUID actor,UUID task,UUID notice,UUID id,String number) {
        mapper.openDraft(bytes(tenant),actor.toString(),task.toString(),notice.toString(),id.toString(),number);
    }

    /** {@inheritDoc} */
    @Override public void insertDraft(UUID tenant, UUID actor, Draft draft, boolean existing) {
        var key = bytes(tenant);
        var use = draft.references();
        var draftSnapshot = json.writeValueAsString(Map.of("method", draft.method(), "qualityState",
                draft.qualityState(), "physicalNetKg", draft.netKg(), "quantityEvidence", use.quantity()));
        saveHeader(key, actor.toString(), existing, new InboundReceiptOrderMapper.HeaderInsert(draft.id().toString(),
                draft.taskId().toString(), draft.noticeId().toString(), draft.meteringId().toString(),
                draft.qualityId().toString(), draft.receiptNo(), draft.qualityState(), draft.netKg(), draft.netKg(), draftSnapshot,
                draft.replacesReceiptId() == null ? null : draft.replacesReceiptId().toString()));
        mapper.insertLine(key, actor.toString(), new InboundReceiptOrderMapper.LineInsert(draft.lineId().toString(),
                draft.id().toString(), draft.noticeId().toString(), draft.noticeLineId().toString(),
                use.position().locationId(), draft.batchNo(), use.quantity().baseUnitId(), use.quantity().baseQuantity(),
                draft.netKg(), json.writeValueAsString(use),null,null,null));
    }

    /** {@inheritDoc} */
    @Override public SteelMeter lockSteelMeter(UUID tenant,UUID task) {
        var row=mapper.lockSteelMeter(bytes(tenant),task.toString());
        return row==null || row.heatNo()==null?null:new SteelMeter(UUID.fromString(row.id()),row.version(),
                UUID.fromString(row.qualityId()),row.heatNo(),row.method(),row.physicalNetKg(),row.theoreticalKg(),row.packages());
    }

    /** {@inheritDoc} */
    @Override public void insertSteelDraft(UUID tenant,UUID actor,SteelDraft draft, boolean existing) {
        var key=bytes(tenant);String account=actor.toString();
        saveHeader(key,account,existing,new InboundReceiptOrderMapper.HeaderInsert(draft.id().toString(),
                draft.taskId().toString(),draft.noticeId().toString(),draft.meteringId().toString(),draft.qualityId().toString(),
                draft.number(),"QUALIFIED",draft.physicalKg(),draft.accountingKg(),
                json.writeValueAsString(Map.of("method",draft.method(),"qualityState","QUALIFIED","granularity","LPN")),null));
        for(var line:draft.lines()) {
            var ref=line.references();
            var item=json.valueToTree(ref);
            if(!(item instanceof tools.jackson.databind.node.ObjectNode snapshot))
                throw new IllegalStateException("入库行快照格式无效");
            snapshot.put("specification",line.specification());
            snapshot.put("material",line.material());
            mapper.insertLine(key,account,new InboundReceiptOrderMapper.LineInsert(line.id().toString(),
                    draft.id().toString(),draft.noticeId().toString(),line.noticeLineId().toString(),ref.position().locationId(),
                    line.batch(),ref.quantity().baseUnitId(),ref.quantity().baseQuantity(),line.accountingKg(),json.writeValueAsString(snapshot),
                    line.lpn(),line.heatNo(),json.writeValueAsString(Map.of("theoreticalKg",line.theoreticalKg(),"basisKg",line.accountingKg()))));
        }
    }

    /** {@inheritDoc} */
    @Override public int advanceTask(UUID tenant, UUID actor, UUID task, long version) {
        return mapper.advanceTask(bytes(tenant), actor.toString(), task.toString(), version);
    }

    private void saveHeader(byte[] tenant,String actor,boolean existing,InboundReceiptOrderMapper.HeaderInsert row) {
        if (!existing) {
            mapper.insertHeader(tenant,actor,row);
        } else if (mapper.completeHeader(tenant,actor,row)!=1) {
            throw new Rejection(409,"INBOUND_RECEIPT_DRAFT_VERSION_CONFLICT");
        }
    }

    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
