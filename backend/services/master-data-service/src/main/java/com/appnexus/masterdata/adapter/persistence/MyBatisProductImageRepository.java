package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import com.appnexus.masterdata.application.product.ProductImageRepository;
import org.springframework.stereotype.Component;
/** 固定商品存储适配；类型与语义参数取自业务端口，Mapper位置绑定只存在技术边界。 */
@Component
public final class MyBatisProductImageRepository implements ProductImageRepository {
 private final ProductImageMapper mapper;
 /** @param mapper 固定SQL映射，与父聚合使用同一事务连接 */
 public MyBatisProductImageRepository(ProductImageMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> list(String tenant,String product){return ProductStorageValues.stable(mapper.list(tenant,product));}
 /** {@inheritDoc} */
 public Integer count(String tenant,String product){return mapper.count(tenant,product);}
 /** {@inheritDoc} */
 public int insert(String id,String tenant,String product,byte[] bytes,byte[] digest,int width,int height,String creator,String updater){return mapper.insert(id,tenant,product,bytes,digest,width,height,creator,updater);}
 /** {@inheritDoc} */
 public int detach(String actor,String tenant,String product,String id){return mapper.detach(actor,tenant,product,id);}
 /** {@inheritDoc} */
 public int advanceParent(String actor,String tenant,String product,long version){return mapper.advanceParent(actor,tenant,product,version);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> content(String tenant, String product, String id){return ProductStorageValues.stable(mapper.content(tenant,product,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> parent(String tenant, String id, boolean lock){return ProductStorageValues.stable(mapper.parent(tenant,id,lock));}
}
