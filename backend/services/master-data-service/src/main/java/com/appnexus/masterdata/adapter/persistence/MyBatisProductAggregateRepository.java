package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.application.product.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.util.function.BiFunction;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
/** 商品聚合固定持久化组合；所有适配器共用原事务连接，不创建独立提交。 */
@Component
public final class MyBatisProductAggregateRepository implements ProductAggregateRepository {
 private final ProductSnapshotMapper snapshots;private final ProductHistoryMapper history;private final ProductAttributeValueMapper values;private final ProductRepository records;private final ObjectMapper json=new ObjectMapper();
 /** @param snapshots 固定快照SQL @param history 固定历史SQL @param values 固定属性存取 @param records 同聚合父记录端口 */
 public MyBatisProductAggregateRepository(ProductSnapshotMapper snapshots,ProductHistoryMapper history,ProductAttributeValueMapper values,ProductRepository records){this.snapshots=snapshots;this.history=history;this.values=values;this.records=records;}
 public Map<String,Object> changes(VerifiedTenantContext c,String id,int page,int size){var tenant=c.tenantId();var bytes=java.nio.ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();return Map.of("rows",history.list(bytes,id,size,(page-1)*size),"total",history.count(bytes,id));}
 public Map<String,Object> snapshot(VerifiedTenantContext c,String id,BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues){return new MyBatisProductSnapshot(snapshots,records,issues).load(c,id);}
 public String appendHistory(VerifiedTenantContext c,String id,String type,Map<String,Object> before,String reason,BiFunction<VerifiedTenantContext,String,Map<String,Object>> snapshot){return new MyBatisProductHistory(history,json,snapshot).append(c,id,type,before,reason);}
 public ValueHistory valueHistory(VerifiedTenantContext c,String id){var result=new MyBatisProductAttributes(values).history(c,id);return new ValueHistory(ProductStorageValues.stable(result.values()),ProductStorageValues.stable(result.choices()));}
 public void replaceValues(VerifiedTenantContext c,String id,String config,List<ProductAttributePreparation.PreparedValue> prepared,ValueHistory preserved){new MyBatisProductAttributes(values).replace(c,id,config,prepared,preserved);}
}
