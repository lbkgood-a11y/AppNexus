package com.appnexus.platform.persistence.configuration;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 新租户开通字典预置；复用V102不可变标准清单，不调用历史JDBC迁移助手。 */
@Component
public class MyBatisDictionaryProvisioning {
    private final DictionaryProvisioningMapper mapper;
    /** @param mapper 与租户开通事务共用连接的固定Mapper */
    public MyBatisDictionaryProvisioning(DictionaryProvisioningMapper mapper){this.mapper=mapper;}
    /**
     * 按稳定标识补齐标准配置，失败与租户、首位成员及授权一起回滚。
     * @param tenant 新开通可信租户UUID，禁止从普通业务参数指定其他租户
     * @param actor 真实开通账号UUID；系统执行允许null，不伪造账号
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public void seed(UUID tenant,UUID actor){
        String tenantId=tenant.toString(),actorId=actor==null?null:actor.toString();int order=0;
        for(var entry:StandardDictionaryDefaults.entries()){
            order++;
            String id=stableId(tenantId+":"+entry.dictionaryCode());
            mapper.createDictionary(id,tenantId,entry.dictionaryCode(),entry.dictionaryName(),order,actorId);
            var dictionaries=mapper.dictionary(tenantId,entry.dictionaryCode());if(dictionaries.isEmpty())continue;
            mapper.createItem(stableId(tenantId+":"+entry.dictionaryCode()+":"+entry.value()),tenantId,dictionaries.getFirst(),entry.code(),entry.value(),entry.name(),order,actorId);
        }
    }
    private static String stableId(String source){return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();}
}
