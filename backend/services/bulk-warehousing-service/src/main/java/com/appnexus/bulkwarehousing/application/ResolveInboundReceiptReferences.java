package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.Objects;

/** 编排进仓引用核验，确保实收依据与库存基础单位一致。 */
public final class ResolveInboundReceiptReferences implements InboundReceiptReferences {
  private final InboundReferenceSource references;

  /** @param references 已装配的授权主数据出站端口 */
  public ResolveInboundReceiptReferences(InboundReferenceSource references) {
    this.references = references;
  }

  /** {@inheritDoc} */
  @Override
  public Resolved resolve(VerifiedTenantContext context, Request request) {
    if (request == null || request.position() == null || request.item() == null
        || request.item().kind() == null || request.conversion() == null) {
      throw new IllegalArgumentException("INBOUND_REFERENCES_INVALID");
    }
    var position = references.position(context, request.position());
    var item = request.item();
    var identity = references.identity(context, item);
    if (request.ownerPartnerId() == null) throw new IllegalArgumentException("INBOUND_OWNER_UNAVAILABLE");
    var owner = references.owner(context, request.ownerPartnerId());
    var quantity = quantity(context, request, identity.baseUnitId());
    return new Resolved(position, item, identity.specificationId(), identity.version(),
        owner.id(), owner.version(), quantity);
  }

  private QuantityEvidence quantity(VerifiedTenantContext context, Request request, String baseUnit) {
    if (request.quantity() == null || !request.quantity().matches("(0|[1-9][0-9]{0,17})([.][0-9]{1,12})?")) {
      throw new IllegalArgumentException("INBOUND_QUANTITY_INVALID");
    }
    BigDecimal amount = new BigDecimal(request.quantity());
    if (amount.signum() <= 0) throw new IllegalArgumentException("INBOUND_QUANTITY_INVALID");

    return switch (request.conversion()) {
      case SameUnit direct -> {
        requireUnits(request.sourceUnitId(), baseUnit, baseUnit, baseUnit);
        yield new QuantityEvidence(baseUnit, baseUnit, amount, amount, BigDecimal.ONE, null, direct);
      }
      case UnitRule rule -> {
        var evidence = references.unitConversion(context, rule, request.quantity());
        requireUnits(request.sourceUnitId(), baseUnit, evidence.sourceUnitId(), evidence.targetUnitId());
        yield new QuantityEvidence(evidence.sourceUnitId(), evidence.targetUnitId(), amount,
            evidence.result(), evidence.factor(), evidence.scale(), rule);
      }
      case Packaging packaging -> {
        var item = request.item();
        var evidence = references.packaging(context, item, packaging, request.quantity());
        requireUnits(request.sourceUnitId(), baseUnit, evidence.sourceUnitId(), evidence.targetUnitId());
        yield new QuantityEvidence(evidence.sourceUnitId(), evidence.targetUnitId(), amount,
            evidence.result(), evidence.factor(), evidence.scale(), packaging);
      }
    };
  }

  // 即使换算规则本身有效，也必须与本次来源单位及商品的库存基础单位一致。
  private static void requireUnits(String source, String target, String ruleSource, String ruleTarget) {
    if (!Objects.equals(source, ruleSource) || !Objects.equals(target, ruleTarget)) {
      throw new IllegalArgumentException("INBOUND_CONVERSION_UNIT_MISMATCH");
    }
  }
}
