package com.appnexus.bulkwarehousing.adapter.persistence;

import static com.appnexus.bulkwarehousing.adapter.persistence.MyBatisOutboundStockStore.*;

import com.appnexus.bulkwarehousing.api.CoilTitleTransfer.*;
import com.appnexus.bulkwarehousing.application.CoilTitleStore;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;

/** 原仓库事务内持久化过户及相反分录。 */
public final class MyBatisCoilTitleStore implements CoilTitleStore {
  private final CoilTitleMapper mapper;

  /** @param mapper 受可信租户拦截的固定仓储 SQL */
  public MyBatisCoilTitleStore(CoilTitleMapper mapper) { this.mapper=mapper; }

  /** {@inheritDoc} */
  @Override public Posted transfer(VerifiedTenantContext c,String id) {
    var row=mapper.transfer(tenant(c),id);
    if (row==null) return null;
    var result=new Transfer(id,text(row,"receiptId"),text(row,"warehouseId"),text(row,"locationId"),
        text(row,"fromOwnerId"),text(row,"toOwnerId"),text(row,"unitId"),
        ((BigDecimal)row.get("quantity")).stripTrailingZeros(),((Number)row.get("revision")).longValue());
    return new Posted(result,text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public long nextRevision(VerifiedTenantContext c) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.ensure(tenant,actor);
    mapper.advance(tenant,actor);
    return mapper.watermark(tenant);
  }

  /** {@inheritDoc} */
  @Override public void transfer(VerifiedTenantContext c,Command command,Transfer result,String digest) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertTransfer(tenant,actor,result,command.evidenceId(),digest);
    mapper.entry(tenant,actor,result.id(),"OUT",result.fromOwnerId(),result.quantity().negate());
    mapper.entry(tenant,actor,result.id(),"IN",result.toOwnerId(),result.quantity());
    if (mapper.assignOwner(tenant,actor,result,command.expectedTitleRevision())!=1) {
      throw new Rejection(409,"WAREHOUSING_TITLE_VERSION_CHANGED");
    }
  }
}
