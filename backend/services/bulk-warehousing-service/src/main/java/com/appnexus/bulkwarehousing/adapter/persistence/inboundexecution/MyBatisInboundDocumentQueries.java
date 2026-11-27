package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.Creation;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.TaskOption;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.LabMetricOption;
import java.util.List;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.Kind;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.InboundDocumentPage;
import com.appnexus.bulkwarehousing.application.inboundexecution.InboundDocumentQueries;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 固定读操作复用公开事务许可，列表总数和明细均读取同一快照。 */
public final class MyBatisInboundDocumentQueries implements InboundDocumentQueries {
    private final InboundDocumentMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;
    private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();

    /** @param mapper 固定 SQL @param gate 公共事务许可 @param manager 组合应用主库事务 */
    public MyBatisInboundDocumentQueries(InboundDocumentMapper mapper,ScopedWriteGate gate,PlatformTransactionManager manager) {
        this.mapper=mapper;
        this.gate=gate;
        this.transaction=new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** {@inheritDoc} */
    @Override public InboundDocumentPage list(AccessPlan plan,Kind kind,String number,int offset,int limit) {
        return transaction.execute(status -> {
            var tenant = bind(plan);
            return switch (kind) {
                case QUALITY -> new InboundDocumentPage(mapper.listQuality(tenant,number,offset,limit),mapper.countQuality(tenant,number));
                case METERING -> new InboundDocumentPage(mapper.listMetering(tenant,number,offset,limit),mapper.countMetering(tenant,number));
                case RECEIPT -> new InboundDocumentPage(mapper.listReceipt(tenant,number,offset,limit),mapper.countReceipt(tenant,number));
            };
        });
    }

    /** {@inheritDoc} */
    @Override public Map<String,Object> detail(AccessPlan plan,Kind kind,UUID id) {
        return transaction.execute(status -> {
            var tenant=bind(plan);
            String record=id.toString();
            var row=switch(kind) {
                case QUALITY -> mapper.readQuality(tenant,record);
                case METERING -> mapper.readMetering(tenant,record);
                case RECEIPT -> mapper.readReceipt(tenant,record);
            };
            if(row==null || kind!=Kind.RECEIPT)return row;
            var result=new LinkedHashMap<>(row);
            result.put("lines",mapper.receiptLines(tenant,record));
            return result;
        });
    }

    /** {@inheritDoc} */
    @Override public List<TaskOption> taskOptions(AccessPlan plan, Creation creation, String prefix) {
        return transaction.execute(status -> {
            byte[] tenant = bind(plan);
            String pattern = prefix.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            var rows = switch (creation) {
                case SAMPLE -> mapper.optionsSAMPLE(tenant, pattern);
                case INSPECT -> mapper.optionsINSPECT(tenant, pattern);
                case GROSS -> mapper.optionsGROSS(tenant, pattern);
                case EXTERNAL -> mapper.optionsEXTERNAL(tenant, pattern);
                case DUAL -> mapper.optionsDUAL(tenant, pattern);
                case BULK -> mapper.optionsBULK(tenant, pattern);
                case LPN -> mapper.optionsLPN(tenant, pattern);
            };
            return rows.stream().map(row -> new TaskOption(UUID.fromString(row.taskId()), row.taskNo(),
                    row.taskVersion(), creation.kind() == Kind.RECEIPT ? row.meteringVersion() : null,
                    creation.kind() == Kind.RECEIPT ? UUID.fromString(row.noticeId()) : null)).toList();
        });
    }

    /** {@inheritDoc} */
    @Override public List<LabMetricOption> labMetrics(AccessPlan plan,UUID id) {
        return transaction.execute(status->{
            String snapshot=mapper.labMetrics(bind(plan),id.toString());
            if(snapshot==null)return List.of();
            return json.readTree(snapshot).properties().stream()
                    .map(row->new LabMetricOption(row.getKey(),row.getValue().path("unit").asText()))
                    .sorted(java.util.Comparator.comparing(LabMetricOption::code)).toList();
        });
    }

    private byte[] bind(AccessPlan plan) {
        gate.authorizeOperation(plan);
        var tenant=plan.subject().tenantId();
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
