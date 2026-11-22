package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;

/** 货权库存和授权额度的两个独立只读存储投影。 */
public interface WarehouseBalanceStore {
  /** 读取原实收及当前仓储事实。缺失返回空。 */
  TraceStock traceStock(VerifiedTenantContext c,String id);
  /**
   * @param document 现有追溯响应所需的业务事件
   * @param previousWarehouseId 改仓前的仓库，用于响应前授权校验，其他事件为 null
   * @param responsiblePartnerId 短损责任方，仅用于权限核对，其他事件为 null
   */
  record TraceEvent(TraceDocument document, String previousWarehouseId, String responsiblePartnerId) {}

  /** 读取真实业务流水及其权限事实。权限核对由应用在响应前执行。 */
  List<TraceEvent> traceDocuments(VerifiedTenantContext c,String id);

  /** 一处同单位品规的账面余额及通过库存状态检查的数量，仍需复核位置。 */
  record StockSlice(Position position,String identityId,String unitId,BigDecimal title,BigDecimal eligible) {}

  /** 与现有库存位置解析复核所需的整卷快照。 */
  record CoilSlice(Position position,String receiptId,String serial,String identityId,String unitId,
                   BigDecimal quantity,long titleRevision,String status) {}
  /** 按实际库存身份、现行货权和指定范围读取整卷。 */
  List<CoilSlice> coils(VerifiedTenantContext c,Scope scope,String afterId,int limit);

  /** 读取货主当前在库的货权，按库存位置分组。 */
  List<StockSlice> physical(VerifiedTenantContext c,Scope scope);

  /** 读取定向授权额度，独立于实物库存。 */
  List<AuthorizationBalance> authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit);
}
