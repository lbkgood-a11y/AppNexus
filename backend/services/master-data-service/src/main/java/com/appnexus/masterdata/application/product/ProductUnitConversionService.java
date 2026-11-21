package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.application.product.UnitConversionRepository;

import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import com.appnexus.masterdata.api.product.UnitConversions;
import com.appnexus.masterdata.application.RecordTransaction;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.appnexus.masterdata.domain.product.UnitConversionRules.*;

/** 单位换算用例；协调真实授权、固定锁顺序、领域规则及原子写入，不依赖 JDBC 或事务框架。 */
public final class ProductUnitConversionService implements UnitConversions {
  private final UnitConversionRepository store;
  private final AccessAuthorization access;
  private final RecordTransaction transaction;
  /** @param store 租户限定的固定存储端口 @param access 每次调用复核的可信授权端口 @param transaction 与存储同数据源的 REQUIRED 事务 */
  public ProductUnitConversionService(UnitConversionRepository store, AccessAuthorization access, RecordTransaction transaction) {
    this.store = store; this.access = access; this.transaction = transaction;
  }
  /** {@inheritDoc} */
  @Override public Object list(VerifiedTenantContext c) { require(c, "read"); return store.list(c.tenantId().toString()); }
  /** {@inheritDoc} */
  @Override public Object create(VerifiedTenantContext c, Input input) {
    require(c, "convert"); if (input == null) throw invalid();
    identifier(input.sourceUnitId()); identifier(input.targetUnitId());
    if (input.sourceUnitId().equals(input.targetUnitId())) throw invalid();
    var factor = decimal(input.factor(), true);
    return transaction.execute(() -> {
      var ids = List.of(input.sourceUnitId(), input.targetUnitId()).stream().sorted().toList();
      var first = enabled(c, ids.getFirst()); var second = enabled(c, ids.getLast());
      sameDimension(first.dimension(), second.dimension());
      reciprocal(factor, store.reverse(c.tenantId().toString(), input.targetUnitId(), input.sourceUnitId()));
      String id = UUID.randomUUID().toString();
      store.create(c.tenantId().toString(), id, input.sourceUnitId(), input.targetUnitId(), factor, c.accountId().toString());
      return Map.of("id", id);
    }, "PRODUCT_CONVERSION_CONFLICT");
  }
  /** {@inheritDoc} */
  @Override public Object status(VerifiedTenantContext c, String id, Status input) {
    require(c, "convert"); identifier(id);
    if (input == null || input.enabled() == null || input.version() == null || input.version() < 0) throw invalid();
    return transaction.execute(() -> {
      if (input.enabled()) {
        var relation = store.relation(c.tenantId().toString(), id); if (relation == null) throw invalid();
        // 与新建相同顺序固定两端单位，避免反向并发启用越过往返检查。
        var ids = List.of(relation.source(), relation.target()).stream().sorted().toList();
        var first = enabled(c, ids.getFirst());
        var second = enabled(c, ids.getLast());
        sameDimension(first.dimension(), second.dimension());
        reciprocal(relation.factor(), store.reverse(c.tenantId().toString(), relation.target(), relation.source()));
      }
      if (store.status(c.tenantId().toString(), id, input.enabled(), input.version(), c.accountId().toString()) != 1)
        throw new Rejection(409, "MASTER_DATA_CHANGED");
      return Map.of("ok", true);
    }, "PRODUCT_CONVERSION_CONFLICT");
  }
  /** {@inheritDoc} */
  @Override public Object calculate(VerifiedTenantContext c, String id, String quantity) {
    require(c, "read"); identifier(id); var amount = decimal(quantity, false);
    var facts = store.calculation(c.tenantId().toString(), id); if (facts == null) throw invalid();
    return com.appnexus.masterdata.domain.product.UnitConversionRules.calculate(amount, facts.factor(), facts.scale());
  }
  private UnitConversionRepository.Unit enabled(VerifiedTenantContext c, String id) {
    var unit = store.lockUnit(c.tenantId().toString(), id);
    if (unit == null || !unit.enabled()) throw invalid(); return unit;
  }
  private void require(VerifiedTenantContext c, String action) {
    if (c == null || !access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(), c.tenantId(), c.memberId()),
        "masterdata.productunit:" + action)) throw new Rejection(403, "MASTER_DATA_DENIED");
  }
}
