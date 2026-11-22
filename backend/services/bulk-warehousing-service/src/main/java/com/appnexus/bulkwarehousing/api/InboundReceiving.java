package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 入仓写入入口。单条通知按基础单位控制累计实收，每笔接管对应一个可独立确认的范围。 */
public interface InboundReceiving {
  /** 签发请求，可使用旧单行字段或新的非空明细集合。单号缺省使用通知 UUID。 */
  record Notice(String id, String warehouseId, String ownerPartnerId,
                InboundReceiptReferences.Item item, String baseUnitId, BigDecimal limit,
                String noticeNo, Instant expectedArrivalAt, String remark, List<NoticeLine> lines) {
    /** 兼容旧单行 API 和原履约宿主。 */
    public Notice(String id,String warehouseId,String ownerPartnerId,InboundReceiptReferences.Item item,
        String baseUnitId,BigDecimal limit) {
      this(id,warehouseId,ownerPartnerId,item,baseUnitId,limit,null,null,null,null);
    }
  }

  /** 批准上限和可选计划量统一使用该行的基础单位，明细 UUID 由签发方保持稳定。 */
  record NoticeLine(String id,InboundReceiptReferences.Item item,String baseUnitId,BigDecimal limit,
                    BigDecimal plannedQuantity,String remark) {}

  /** 实际接管的范围及现场证据标识，位置版本由受控主数据提供。 */
  record Custody(String id,String noticeId,InboundReceiptReferences.Position position,String evidenceId,
      String noticeLineId,String arrivalBatchNo,String entryId,BigDecimal expectedQuantity) {
    /** 单行通知可以按旧格式选择唯一明细。 */
    public Custody(String id,String noticeId,InboundReceiptReferences.Position position,String evidenceId) {
      this(id,noticeId,position,evidenceId,null,null,null,null);
    }
    /** 兼容按通知明细的原接管请求。 */
    public Custody(String id,String noticeId,InboundReceiptReferences.Position position,String evidenceId,
        String noticeLineId,String arrivalBatchNo) {
      this(id,noticeId,position,evidenceId,noticeLineId,arrivalBatchNo,null,null);
    }
  }

  /** @param vehicleNumber 实际车牌或船次，最多 64 字符 @param arrivedAt 实际入场 UTC 时间
   * @param lines 1–100 个同张通知的接管范围，保存整个批次或一起回滚 */
  record Entry(String id,String noticeId,String vehicleNumber,String carrier,Instant arrivedAt,String remark,List<EntryLine> lines) {}

  /** @param expectedQuantity 可选到货量，使用原通知行基础单位 */
  record EntryLine(String id,String noticeLineId,InboundReceiptReferences.Position position,String evidenceId,BigDecimal expectedQuantity) {}

  /** @param measurementRef 可选磅单或计量单号，最长 64 字符
   * @param lines 同一入场批次的 1–100 个待确认接管范围 */
  record ReceiptDocument(String id,String entryId,String measurementRef,String remark,List<ReceiptLine> lines) {}

  /** 实收和拒收都以该通知行基础单位计量；正实收必须有独立计量证据。 */
  record ReceiptLine(String id,String custodyId,String measurementId,BigDecimal quantity,BigDecimal rejectedQuantity,String rejectionReason) {}

  /** @param context 可信租户成员 @param entry 多品规入场接管单，事务内一起登记 */
  void recordEntry(VerifiedTenantContext context,Entry entry);

  /** @param context 可信租户成员 @param document 实收主单与勾选的接管明细，事务内一起确认 */
  void confirmReceiptDocument(VerifiedTenantContext context,ReceiptDocument document);

  /** 实收请求。ID 为重试时保持不变的业务键；来源数量及单位须对应所引用的有效计量证据。 */
  record Receipt(String id, String custodyId, String measurementId, InboundReceiptReferences.Request references,
      String receiptBatchNo) {
    /** 兼容原调用者，缺省批次号表示未记录。 */
    public Receipt(String id,String custodyId,String measurementId,InboundReceiptReferences.Request references) {
      this(id,custodyId,measurementId,references,null);
    }
  }

  /** 已确认的实收及待检库存过账结果，数量以基础单位表达。 */
  record Posting(String receiptId, String custodyId, BigDecimal quantity, String unitId) {}

  /** @param context 可信租户和成员 @param notice 已获授权的执行范围 */
  void issue(VerifiedTenantContext context, Notice notice);

  /** @param context 可信租户和成员 @param custody 实际控制范围及现场证据 */
  void takeCustody(VerifiedTenantContext context, Custody custody);

  /** @param context 可信租户和成员 @param receipt 经岗位复核的实收依据 @return 已过账结果或同键重放结果 */
  Posting receive(VerifiedTenantContext context, Receipt receipt);
}
