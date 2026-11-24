package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.InboundReceiving.*;
import com.appnexus.bulkwarehousing.application.InboundReceivingStore;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 入仓通知 MyBatis 适配器，事务覆盖签发主单及明细。 */
public final class MyBatisInboundReceivingStore implements InboundReceivingStore {
  private final InboundReceivingMapper mapper;
  private final TransactionTemplate transaction;

  /** @param mapper 固定仓储语句 @param manager 组合应用共用同一数据源的事务管理器 */
  public MyBatisInboundReceivingStore(InboundReceivingMapper mapper, PlatformTransactionManager manager) {
    this.mapper=mapper;
    this.transaction=new TransactionTemplate(manager);
  }

  /** {@inheritDoc} */
  @Override public <T> T transaction(Supplier<T> work) {
    try {
      return transaction.execute(status -> work.get());
    } catch (DuplicateKeyException error) {
      throw new Rejection(409,"INBOUND_REQUEST_CONFLICT");
    }
  }

  /** {@inheritDoc} */
  @Override public void issue(VerifiedTenantContext c,Notice notice,int flowVersion) {
    var legacy=notice.item();
    mapper.issue(tenant(c),actor(c),notice,flowVersion,legacy==null?null:legacy.productId(),
        legacy==null?null:legacy.kind().name(),legacy==null?null:legacy.identityId(),
        legacy==null?null:legacy.productVersion());
    for (var line:notice.lines()) mapper.issueLine(tenant(c),actor(c),notice.id(),line);
  }

  /** {@inheritDoc} */
  @Override public Existing lock(VerifiedTenantContext context,String id) {
    var row=mapper.lockHeader(tenant(context),id);
    return row==null?null:new Existing((String)row.get("warehouseId"),(String)row.get("ownerId"),
        ((Number)row.get("version")).longValue(),(String)row.get("status"),
        mapper.lockLines(tenant(context),id).stream().map(line->(String)line.get("productId")).distinct().toList(),new Driver((String)row.get("driverName"),(String)row.get("driverPhone"),(String)row.get("driverIdNumber")));
  }

  /** {@inheritDoc} */
  @Override public void update(VerifiedTenantContext context,Notice notice,long version) {
    var tenant=tenant(context);
    var original=mapper.lockHeader(tenant,notice.id());
    var prior=mapper.lockLines(tenant,notice.id());
    var byId=prior.stream().collect(java.util.stream.Collectors.toMap(row->(String)row.get("id"),row->row));
    boolean posted=prior.stream().anyMatch(row->((java.math.BigDecimal)row.get("received")).signum()>0);
    // 已形成库存的原单不能更换仓库、货主、商品或单位，限额不得低于实收。
    if(posted && (!notice.warehouseId().equals(original.get("warehouseId")) || !notice.ownerPartnerId().equals(original.get("ownerId"))))
      throw new Rejection(409,"INBOUND_POSTED_CONTENT_CONFLICT");
    for(var line:notice.lines()){
      var old=byId.remove(line.id());
      if(old==null){mapper.issueLine(tenant,actor(context),notice.id(),line);continue;}
      var received=(java.math.BigDecimal)old.get("received");
      if(received.compareTo(line.limit())>0 || (received.signum()>0 && (
          !line.item().productId().equals(old.get("productId")) || !line.item().identityId().equals(old.get("identityId"))
          || !line.item().kind().name().equals(old.get("kind")) || !line.baseUnitId().equals(old.get("unitId")))))
        throw new Rejection(409,"INBOUND_POSTED_CONTENT_CONFLICT");
      if(mapper.updateLine(tenant,actor(context),notice.id(),line)!=1)throw new Rejection(409,"INBOUND_VERSION_CONFLICT");
    }
    for(var id:byId.keySet()){
      if(mapper.deleteLine(tenant,notice.id(),id)!=1)throw new Rejection(409,"INBOUND_POSTED_CONTENT_CONFLICT");
    }
    if(mapper.updateHeader(tenant,actor(context),notice,version)!=1)throw new Rejection(409,"INBOUND_VERSION_CONFLICT");
  }

  private static byte[] tenant(VerifiedTenantContext c) {
    var id=c.tenantId();
    return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits()).array();
  }
  private static String actor(VerifiedTenantContext c) { return c.accountId().toString(); }
}
