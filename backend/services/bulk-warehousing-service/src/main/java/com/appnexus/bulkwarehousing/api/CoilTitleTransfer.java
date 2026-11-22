package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;

/** 同仓不动货整卷过户。货权更改不改变库存身份、仓库、库位或原账面数量。 */
public interface CoilTitleTransfer {
  String OBJECT="BulkCoilTitleTransfer";
  String RESOURCE="warehousing.coil.title";
  String PERMISSION=RESOURCE+":transfer";

  /** 经批准的原仓、原货主、目标货主及权利凭证。初次入仓权利版本为 0。 */
  record Command(String id,String receiptId,String warehouseId,String fromOwnerId,String toOwnerId,
                 long expectedTitleRevision,String evidenceId) {}

  /** 过户结果，数量取自权威在库整卷，库位保留。 */
  record Transfer(String id,String receiptId,String warehouseId,String locationId,String fromOwnerId,
                  String toOwnerId,String unitId,BigDecimal quantity,long titleRevision) {}

  /** @param c 可信过户岗位 @param command 审批依据及预期货权 @return 已过账的权利划转 */
  Transfer transfer(VerifiedTenantContext c,Command command);
}
