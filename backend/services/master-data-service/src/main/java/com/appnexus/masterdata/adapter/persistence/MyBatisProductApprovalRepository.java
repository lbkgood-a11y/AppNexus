package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import com.appnexus.masterdata.application.product.ProductApprovalRepository;
import org.springframework.stereotype.Component;
/** 固定商品存储适配；类型与语义参数取自业务端口，Mapper位置绑定只存在技术边界。 */
@Component
public final class MyBatisProductApprovalRepository implements ProductApprovalRepository {
 private final ProductApprovalMapper mapper;
 /** @param mapper 固定SQL映射，与父聚合使用同一事务连接 */
 public MyBatisProductApprovalRepository(ProductApprovalMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public Map<String,Object> lockProduct(String tenant,String product){return ProductStorageValues.stable(mapper.lockProduct(tenant,product));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> existingRequest(String tenant,String applicant,String requestKey){return ProductStorageValues.stable(mapper.existingRequest(tenant,applicant,requestKey));}
 /** {@inheritDoc} */
 public int freezeProductVersion(String actor,String tenant,String product){return mapper.freezeProductVersion(actor,tenant,product);}
 /** {@inheritDoc} */
 public int createApproval(String id,String tenant,String product,String requestKey,long productVersion,String applicant,String organization,String snapshot,String creator,String updater){return mapper.createApproval(id,tenant,product,requestKey,productVersion,applicant,organization,snapshot,creator,updater);}
 /** {@inheritDoc} */
 public int bindExecution(String instance,String resolution,String tenant,String id){return mapper.bindExecution(instance,resolution,tenant,id);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> approvalHistory(String tenant,String product){return ProductStorageValues.stable(mapper.approvalHistory(tenant,product));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> approvalDetail(String tenant,String id){return ProductStorageValues.stable(mapper.approvalDetail(tenant,id));}
 public String approvalSnapshot(String tenant,String id){return mapper.approvalSnapshot(tenant,id);}
}
