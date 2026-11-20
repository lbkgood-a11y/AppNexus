package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.application.product.ProductCompletenessFacts;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.stereotype.Component;
/** 完整性固定SQL事实适配，不决定必填项、启用条件或完整性状态。 */
@Component
public final class MyBatisProductCompletenessFacts implements ProductCompletenessFacts {
 private final ProductCompletenessMapper mapper;
 /** @param mapper 同事务固定事实与状态写入SQL */
 public MyBatisProductCompletenessFacts(ProductCompletenessMapper mapper){this.mapper=mapper;}
 public Boolean serviceUnitRequired(VerifiedTenantContext c,Object config){return mapper.serviceUnitRequired(tenant(c),config);}
 public List<String> missingAttributes(VerifiedTenantContext c,Object config,Object id){return mapper.missingAttributes(tenant(c),config,id);}
 public long specificationCount(VerifiedTenantContext c,String id){return mapper.specificationCount(tenant(c),id);}
 public List<Map<String,Object>> components(VerifiedTenantContext c,String id){return ProductStorageValues.stable(mapper.components(tenant(c),id));}
 public void refresh(VerifiedTenantContext c,String id,String state){mapper.refresh(state,c.accountId().toString(),tenant(c),id);}
 private byte[] tenant(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
