package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductStructureData.Migration;
import com.appnexus.masterdata.MasterData.Rejection;
import java.util.*;
/** 结构预检应用；只组合真实快照、退出确认和短期证明，不创建执行任务。 */
public final class ProductPreviewService {
  private final ProductPreviewPort port;
  /** @param port 已授权readOnly事务中的只读输出端口 */
  public ProductPreviewService(ProductPreviewPort port){this.port=port;}
  /** @param target 格式已验证的全量目标 @return 原协议预检响应，执行时仍必须重新授权、验证源及签名 */
  public Map<String,Object> preview(Migration target){
    var before=port.source();port.validate(target);
    var issues=port.issues(target);var dropped=port.dropped(target);
    if(!new TreeSet<>(target.droppedItemIds()).equals(new TreeSet<>(dropped)))throw new Rejection(409,"PRODUCT_DROPPED_VALUES_UNCONFIRMED");
    var proof=port.proof(target);var result=new LinkedHashMap<String,Object>();
    result.put("sourceVersion",before.get("version"));result.put("sourceStructureRevision",before.get("structureRevision"));
    result.put("sourceConfigId",before.get("configId"));result.put("sourceStatus",before.get("status"));
    result.put("target",target);result.put("completionIssues",issues);result.put("droppedItemIds",dropped);
    result.put("canonicalPayloadDigest",port.digest(target));result.put("previewToken",proof.token());result.put("expiresAt",proof.expiresAt().toString());
    result.put("executable",issues.isEmpty()||"DRAFT".equals(before.get("status")));return result;
  }
}
