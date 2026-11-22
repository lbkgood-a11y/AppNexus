package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;

/** 按货主、仓和商品分开的货权库存及提货授权读取契约。 */
public interface WarehouseBalances {
  String OBJECT="BulkWarehouseStockBalance";
  String RESOURCE="warehousing.coil.stockbalance";
  String READ_PERMISSION=RESOURCE+":read";

  /** 货主、仓库、商品 UUID 必填。identityId 为可选的具体库存品规 UUID。 */
  record Scope(String ownerId,String warehouseId,String productId,String identityId) {}

  /** 品规和库存单位分别聚合。可提量已扣除待检、冻结、占用和位置不可执行范围。 */
  record Physical(String identityId,String unitId,BigDecimal onHandTitle,BigDecimal pickable) {}

  /** 单张提货授权的单据额度及状态；已关闭或到期时可用单据额度为零。 */
  record AuthorizationBalance(String authorizationId,String noticeId,String identityId,String unitId,
      BigDecimal cap,BigDecimal reserved,BigDecimal issued,BigDecimal unassigned,BigDecimal remaining,String status) {}

  /** 以授权 UUID 续查。没有后续数据时 nextAfterId 为空。 */
  record AuthorizationPage(List<AuthorizationBalance> rows,String nextAfterId) {}

  /** 可见的实物整卷及用于过户的权利版本；状态反映冻结、占用、待检和库位可执行性。 */
  record Coil(String receiptId,String serial,String identityId,String unitId,BigDecimal quantity,
              long titleRevision,String status,boolean transferable) {}
  /** 在库整卷按库存 UUID 续查。 */
  record CoilPage(List<Coil> rows,String nextAfterId) {}

  /** 库存身份当前账面、待检和已登记货权，用库存单位表达。 */
  record TraceStock(String receiptId,String serial,String noticeId,String custodyId,String measurementId,
      String warehouseId,String locationId,String ownerId,String productId,String identityId,String unitId,
      BigDecimal onHand,BigDecimal pending,BigDecimal title,String titleEvidenceId,String qualityEvidenceId) {}
  /** 一张可追溯的业务单据及其上游。仓和货主均为权限范围。 */
  record TraceDocument(String kind,String id,String parentId,String warehouseId,String ownerId,
                       String otherOwnerId,BigDecimal quantity) {}
  /** 按真实库存身份读取历史，不依赖浏览器保存的表单。 */
  record CoilTrace(TraceStock stock,List<TraceDocument> documents) {}

  /** @param c 可信主体 @param receiptId 原实收库存身份 @return 当前实物及授权范围内的历史单据 */
  CoilTrace trace(VerifiedTenantContext c,String receiptId);

  /** @param c 可信主体 @param scope 授权查询范围 @param afterId 可选续查 UUID
   * @param limit 单页 1–100 行 @return 当前货主在库整卷及过户所需版本 */
  CoilPage coils(VerifiedTenantContext c,Scope scope,String afterId,int limit);

  /** @param c 可信主体 @param scope 授权查询范围 @return 按品规及单位分行的实物货权 */
  List<Physical> physical(VerifiedTenantContext c,Scope scope);

  /** @param c 可信主体 @param scope 授权查询范围 @param afterId 可选续查 UUID
   * @param limit 单页 1–100 行 @return 单据额度分页 */
  AuthorizationPage authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit);
}
