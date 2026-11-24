package com.appnexus.bulkwarehousing.application;

import java.util.Set;
import com.appnexus.bulkwarehousing.domain.receiving.ReceivingLimit;
import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.UUID;

/** 核验入仓通知的仓库、货主与品规，并在同一受控事务内写入主单和明细。 */
public final class InboundReceivingService implements InboundReceiving, com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor {
  // 延续既有通知签发的版本 2，已保存的历史版本仍可读取。
  private static final int SIGNED_NOTICE_FLOW_VERSION = 2;

  private final InboundReceivingStore store;
  private final InboundReferenceSource source;
  private final AccessPlanning access;
  private final ScopedWriteGate writes;
  private final com.appnexus.platform.authorization.FieldProtection fields;

  /**
   * @param store 入仓写入及事务端口
   * @param source 已授权的单项主数据读取
   * @param access 现有平台权限计划
   * @param writes 在单据写事务中绑定目标记录的公共许可
   */
  public InboundReceivingService(InboundReceivingStore store, InboundReferenceSource source,
      AccessPlanning access, ScopedWriteGate writes,com.appnexus.platform.authorization.FieldProtection fields) {
    this.store = store;
    this.source = source;
    this.access = access;
    this.writes = writes;
    this.fields = fields;
  }

  /** {@inheritDoc} */
  @Override public java.util.Set<String> enforcedResources() {
    return Set.of(InboundOperations.ISSUE.resource());
  }

  /** {@inheritDoc} */
  @Override
  public void issue(VerifiedTenantContext context, Notice notice) {
    var plan=require(context, InboundOperations.ISSUE);
    var command=normalize(notice);
    checkDriverFields(context,command.driver(),null);
    authorized(plan,() -> {
      source.requireWarehouse(context, command.warehouseId());
      source.owner(context, command.ownerPartnerId());
      validateReferences(context,command);
      writes.authorize(plan,UUID.fromString(notice.id()));
      store.issue(context,command,SIGNED_NOTICE_FLOW_VERSION);
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void update(VerifiedTenantContext context,Update input) {
    var plan=require(context,InboundOperations.UPDATE);
    if(input==null || input.version()<0)throw invalid();
    var command=normalize(input.notice());
    authorized(plan,()->{
      writes.authorize(plan,UUID.fromString(command.id()));
      var original=store.lock(context,command.id());
      if(original==null)throw denied();
      source.requireWarehouse(context,original.warehouseId());
      source.owner(context,original.ownerId());
      for(var productId:original.productIds())source.requireProduct(context,productId);
      if(original.version()!=input.version())throw new Rejection(409,"INBOUND_VERSION_CONFLICT");
      if(!Set.of("DRAFT","ISSUED","RECEIVING").contains(original.status()))
        throw new Rejection(409,"INBOUND_STATE_CONFLICT");
      source.requireWarehouse(context,command.warehouseId());
      source.owner(context,command.ownerPartnerId());
      validateReferences(context,command);
      var driver=checkDriverFields(context,command.driver(),original.driver());
      store.update(context,withDriver(command,driver),input.version());
      return null;
    });
  }

  private void validateReferences(VerifiedTenantContext context,Notice command) {
    for(var line:command.lines()){
      var item=source.identity(context,line.item());
      if(!ReceivingLimit.acceptsNoticeUnit(item.baseUnitId(),line.baseUnitId()))throw invalid();
    }
  }

  private static Notice normalize(Notice notice) {
    if (notice==null) throw invalid();
    uuid(notice.id());
    uuid(notice.warehouseId());
    uuid(notice.ownerPartnerId());
    String number=notice.noticeNo()==null ? notice.id() : notice.noticeNo();
    if (!number.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        || (number.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
            && !number.equalsIgnoreCase(notice.id()))) throw invalid();
    validateText(notice.remark(),500);
    validateText(notice.appointmentNo(),64);
    validateText(notice.plannedConveyance(),64);
    if(notice.driver()!=null){
      validateText(notice.driver().driverName(),64);
      validateText(notice.driver().driverPhone(),32);
      validateText(notice.driver().driverIdNumber(),64);
    }
    var lines=notice.lines();
    boolean legacy=lines==null;
    if (legacy) {
      lines=java.util.List.of(new NoticeLine(notice.id(),notice.item(),notice.baseUnitId(),notice.limit(),null,null));
    } else if (notice.item()!=null || notice.baseUnitId()!=null || notice.limit()!=null || lines.isEmpty() || lines.size()>100) {
      throw invalid();
    }
    var lineIds=new java.util.HashSet<String>();
    for (var line:lines) {
      if (line==null || line.item()==null || line.item().kind()==null) throw invalid();
      uuid(line.id());
      uuid(line.baseUnitId());
      if (!lineIds.add(line.id())) throw invalid();
      positiveQuantity(line.limit());
      if (line.plannedQuantity()!=null) positiveQuantity(line.plannedQuantity());
      validateText(line.remark(),500);
      validateText(line.contractNo(),64);
    }
    return new Notice(notice.id(),notice.warehouseId(),notice.ownerPartnerId(),
        legacy ? notice.item() : null,legacy ? notice.baseUnitId() : null,legacy ? notice.limit() : null,
        number,notice.expectedArrivalAt(),notice.remark(),java.util.List.copyOf(lines),
        notice.appointmentNo(),notice.plannedConveyance(),notice.driver());
  }

  /** 受限字段缺省时保留原值，拒绝以脱敏值或其他文本覆盖原文。 */
  private Driver checkDriverFields(VerifiedTenantContext context,Driver incoming,Driver existing) {
    var modes=fields.restrictions(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),"warehousing.inbound");
    var old=existing==null?new Driver(null,null,null):existing;
    if(incoming==null)return old;
    return new Driver(driverValue(modes,"driverName",incoming.driverName(),old.driverName()),
        driverValue(modes,"driverPhone",incoming.driverPhone(),old.driverPhone()),
        driverValue(modes,"driverIdNumber",incoming.driverIdNumber(),old.driverIdNumber()));
  }

  private static String driverValue(java.util.Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode> modes,
      String field,String incoming,String existing) {
    if(!modes.containsKey(field))return incoming;
    if(incoming!=null && !java.util.Objects.equals(incoming,existing))throw new Rejection(403,"INBOUND_FIELD_DENIED");
    return existing;
  }

  private static Notice withDriver(Notice n,Driver driver) {
    return new Notice(n.id(),n.warehouseId(),n.ownerPartnerId(),n.item(),n.baseUnitId(),n.limit(),
        n.noticeNo(),n.expectedArrivalAt(),n.remark(),n.lines(),n.appointmentNo(),n.plannedConveyance(),driver);
  }

  private <T>T authorized(AccessPlan plan,java.util.function.Supplier<T> work){
    return store.transaction(()->{
      writes.authorizeOperation(plan);
      return work.get();
    });
  }

  private AccessPlan require(VerifiedTenantContext context, InboundOperations operation) {
    if (context == null) throw denied();
    var subject = AccessAuthorization.Subject.tenant(context.accountId(), context.tenantId(), context.memberId());
    var plan=access.plan(subject, operation.permission(), operation.resource());
    if (!plan.tenantWide()) throw denied();
    return plan;
  }

  private static void validateText(String value,int limit) {
    if (value!=null && (value.isBlank() || !value.equals(value.strip())
        || value.codePointCount(0,value.length())>limit || value.codePoints().anyMatch(Character::isISOControl))) {
      throw invalid();
    }
  }

  private static void uuid(String value) {
    try {
      if (!UUID.fromString(value).toString().equals(value)) throw invalid();
    } catch (IllegalArgumentException | NullPointerException error) {
      throw invalid();
    }
  }

  private static void positiveQuantity(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 12
        || quantity.precision() - quantity.scale() > 18) throw invalid();
  }

  private static Rejection invalid() { return new Rejection(400, "INBOUND_INPUT_INVALID"); }
  private static Rejection denied() { return new Rejection(403, "INBOUND_ACCESS_DENIED"); }
}
