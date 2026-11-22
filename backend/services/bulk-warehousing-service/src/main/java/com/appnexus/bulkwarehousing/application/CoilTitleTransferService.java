package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.domain.title.TitleTransferRules;
import static com.appnexus.bulkwarehousing.application.OutboundStockService.*;

import com.appnexus.bulkwarehousing.api.CoilTitleTransfer;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** 整卷过户在仓库和库存控制锁内执行。原有冻结或占用必须先按各自权限处置。 */
public final class CoilTitleTransferService implements CoilTitleTransfer,DataAccessExecutor {
  private final CoilTitleStore titles;
  private final OutboundStockStore stock;
  private final CoilOutboundStore outbound;
  private final OutboundStockService gate;
  private final InboundReferenceSource masterData;
  private final AccessPlanning access;
  private final ScopedWriteGate writes;

  /** @param titles 货权过账 @param stock 共用库存事务 @param outbound 仓库控制锁
   * @param gate 共用库存事实及准入 @param masterData 现有主数据准入 @param access 统一权限计划 */
  public CoilTitleTransferService(CoilTitleStore titles,OutboundStockStore stock,CoilOutboundStore outbound,
      OutboundStockService gate,InboundReferenceSource masterData,AccessPlanning access,ScopedWriteGate writes) {
    this.titles=titles;
    this.stock=stock;
    this.outbound=outbound;
    this.gate=gate;
    this.masterData=masterData;
    this.access=access;
    this.writes=writes;
  }

  /** {@inheritDoc} */
  @Override public Set<String> enforcedResources() { return Set.of(RESOURCE); }

  /** {@inheritDoc} */
  @Override public Transfer transfer(VerifiedTenantContext c,Command command) {
    if (c==null) throw denied();
    var plan=access.plan(Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),PERMISSION,RESOURCE);
    if (!plan.tenantWide()) throw denied();
    if (command==null || command.expectedTitleRevision()<0) throw invalid();
    uuid(command.id());
    uuid(command.receiptId());
    uuid(command.warehouseId());
    uuid(command.fromOwnerId());
    uuid(command.toOwnerId());
    uuid(command.evidenceId());
    if (command.fromOwnerId().equals(command.toOwnerId())) throw invalid();
    var key=digest(command);
    return stock.transaction(() -> {
      masterData.requireWarehouse(c,command.warehouseId());
      masterData.owner(c,command.fromOwnerId());
      masterData.owner(c,command.toOwnerId());
      outbound.guard(c,command.warehouseId());
      var prior=titles.transfer(c,command.id());
      if (prior!=null) {
        if (!prior.digest().equals(key)) throw conflict();
        return prior.result();
      }
      var facts=gate.authorizedStock(c,command.receiptId(),command.warehouseId());
      if (!command.fromOwnerId().equals(facts.ownerId())) throw denied();
      switch (TitleTransferRules.evaluate(facts.coil(), facts.titleReady(), facts.dispatched(),
          facts.frozen(), facts.occupied(), facts.titleRevision(), command.expectedTitleRevision())) {
        case NOT_TRANSFERABLE -> throw new Rejection(409, "WAREHOUSING_TITLE_NOT_TRANSFERABLE");
        case FROZEN -> throw new Rejection(409, "OUTBOUND_FROZEN");
        case OCCUPIED -> throw new Rejection(409, "OUTBOUND_OCCUPIED");
        case VERSION_CHANGED -> throw new Rejection(409, "WAREHOUSING_TITLE_VERSION_CHANGED");
        case ALLOWED -> { }
      }
      var result=new Transfer(command.id(),facts.receiptId(),command.warehouseId(),facts.position().locationId(),
          command.fromOwnerId(),command.toOwnerId(),facts.unitId(),facts.quantity().stripTrailingZeros(),titles.nextRevision(c));
      UUID owner=UUID.fromString(result.toOwnerId());
      writes.authorize(plan,UUID.fromString(result.id()),owner);
      writes.authorize(plan,UUID.fromString(result.receiptId()),owner);
      titles.transfer(c,command,result,key);
      return result;
    });
  }

  private static String digest(Command command) {
    var value=String.join("|",command.receiptId(),command.warehouseId(),command.fromOwnerId(),
        command.toOwnerId(),Long.toString(command.expectedTitleRevision()),command.evidenceId());
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
