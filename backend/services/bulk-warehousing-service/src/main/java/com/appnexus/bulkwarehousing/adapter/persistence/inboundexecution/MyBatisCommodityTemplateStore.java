package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates.*;
import com.appnexus.bulkwarehousing.application.inboundexecution.CommodityTemplateStore;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 将模板策略映射为既有作业读取的 JSON 定义。 */
public final class MyBatisCommodityTemplateStore implements CommodityTemplateStore {
    private final CommodityTemplateMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;
    private final JsonMapper json=JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** @param mapper 真实租户 SQL @param gate 公共事务许可 @param manager 主库事务 */
    public MyBatisCommodityTemplateStore(CommodityTemplateMapper mapper,ScopedWriteGate gate,PlatformTransactionManager manager) {
        this.mapper=mapper;
        this.gate=gate;
        this.transaction=new TransactionTemplate(manager);
    }
    /** {@inheritDoc} */
    @Override public <T>T transaction(AccessPlan access,Supplier<T> work) {
        try {
            return transaction.execute(status->{gate.authorizeOperation(access);return work.get();});
        } catch(DuplicateKeyException conflict) {
            throw new Rejection(409,"INBOUND_TEMPLATE_VERSION_CONFLICT");
        }
    }
    /** {@inheritDoc} */
    @Override public CommodityTemplatePage list(UUID tenant,int offset,int limit) {
        var key=bytes(tenant);
        return new CommodityTemplatePage(mapper.list(key,offset,limit).stream().map(this::template).toList(),mapper.count(key));
    }
    /** {@inheritDoc} */
    @Override public CommodityTemplateVersion lock(UUID tenant,UUID id) {return template(mapper.lock(bytes(tenant),id.toString()));}
    /** {@inheritDoc} */
    @Override public CommodityTemplateVersion find(UUID tenant,UUID id) {return template(mapper.find(bytes(tenant),id.toString()));}
    /** {@inheritDoc} */
    @Override public List<CommodityTemplateVersion> lockCode(UUID tenant,String code) {
        return mapper.lockCode(bytes(tenant),code).stream().map(this::template).toList();
    }
    /** {@inheritDoc} */
    @Override public void insert(UUID tenant,UUID actor,UUID id,CommodityTemplateDraft draft){mapper.insert(bytes(tenant),actor.toString(),write(id,draft));}
    /** {@inheritDoc} */
    @Override public int update(UUID tenant,UUID actor,UUID id,long version,CommodityTemplateDraft draft){
        return mapper.update(bytes(tenant),actor.toString(),write(id,draft),version);
    }
    /** {@inheritDoc} */
    @Override public void retire(UUID tenant,UUID actor,String code,UUID id){mapper.retire(bytes(tenant),actor.toString(),code,id.toString());}
    /** {@inheritDoc} */
    @Override public int publish(UUID tenant,UUID actor,UUID id,long version){return mapper.publish(bytes(tenant),actor.toString(),id.toString(),version);}
    private CommodityTemplateVersion template(CommodityTemplateMapper.Row row) {
        if(row==null)return null;
        return new CommodityTemplateVersion(UUID.fromString(row.id()),row.status(),row.rowVersion(),new CommodityTemplateDraft(row.code(),row.version(),
                Profile.valueOf(row.profile()),json.readValue(row.definition(),CommodityTemplateDefinition.class)));
    }
    private CommodityTemplateMapper.Write write(UUID id,CommodityTemplateDraft draft) {
        String jsonValue=json.writeValueAsString(draft.definition());
        return switch(draft.profile()) {
            case QUALITY_DEDUCTION -> new CommodityTemplateMapper.Write(id.toString(),draft.code(),draft.version(),
                    "SCALE","QUALIFIED_AND_UNLOADED","QUALITY_LINEAR","BULK",jsonValue);
            case SAMPLED_BULK -> new CommodityTemplateMapper.Write(id.toString(),draft.code(),draft.version(),
                    "SCALE_OR_EXTERNAL","SAMPLED","ZERO","BULK",jsonValue);
            case STEEL_LPN -> new CommodityTemplateMapper.Write(id.toString(),draft.code(),draft.version(),
                    "DUAL","DOCUMENT_AND_APPEARANCE","ZERO","LPN",jsonValue);
        };
    }
    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
