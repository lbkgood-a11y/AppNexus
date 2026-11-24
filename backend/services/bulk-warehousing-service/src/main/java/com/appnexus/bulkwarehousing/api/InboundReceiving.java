package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 入仓通知签发契约。 */
public interface InboundReceiving {
  /**
   * 签发请求，可使用旧单行字段或非空明细集合。单号缺省使用通知 UUID。
   * @param appointmentNo 外部预约单号，可选，最多 64 字符，仅供业务查阅
   * @param driver 可选运输联系人，缺省不采集司机信息
   * @param plannedConveyance 计划车牌或船号，可选，最多 64 字符，实际车牌由入场单记录
   */
  record Notice(String id, String warehouseId, String ownerPartnerId,
                InboundReceiptReferences.Item item, String baseUnitId, BigDecimal limit,
                String noticeNo, Instant expectedArrivalAt, String remark, List<NoticeLine> lines,
                String appointmentNo, String plannedConveyance,Driver driver) {
    /** 兼容已有调用方，未采集司机时三个字段为空。 */
    public Notice(String id,String warehouseId,String ownerPartnerId,InboundReceiptReferences.Item item,String baseUnitId,BigDecimal limit,
        String noticeNo,Instant expectedArrivalAt,String remark,List<NoticeLine> lines,String appointmentNo,String plannedConveyance) {
      this(id,warehouseId,ownerPartnerId,item,baseUnitId,limit,noticeNo,expectedArrivalAt,remark,lines,appointmentNo,plannedConveyance,null);
    }
    /** 兼容已有多行调用方。 */
    public Notice(String id,String warehouseId,String ownerPartnerId,InboundReceiptReferences.Item item,
        String baseUnitId,BigDecimal limit,String noticeNo,Instant expectedArrivalAt,String remark,List<NoticeLine> lines) {
      this(id,warehouseId,ownerPartnerId,item,baseUnitId,limit,noticeNo,expectedArrivalAt,remark,lines,null,null);
    }

    /** 兼容旧单行 API 和原履约宿主。 */
    public Notice(String id,String warehouseId,String ownerPartnerId,InboundReceiptReferences.Item item,
        String baseUnitId,BigDecimal limit) {
      this(id,warehouseId,ownerPartnerId,item,baseUnitId,limit,null,null,null,null);
    }
  }

  /**
   * 司机信息，按单据保存现场联系快照，字段限制由平台统一处理。
   * @param driverName 可选司机姓名，最多64字符
   * @param driverPhone 可选联系电话，最多32字符，允许国际区号与分机
   * @param driverIdNumber 可选证件号，最多64字符，不限定证件类型或国家
   */
  record Driver(String driverName,String driverPhone,String driverIdNumber) {}

  /**
   * 批准上限和可选计划量统一使用该行的基础单位，明细 UUID 由签发方保持稳定。
   * @param contractNo 本行关联的业务合同号，可选，最多 64 字符，仅供查阅
   */
  record NoticeLine(String id,InboundReceiptReferences.Item item,String baseUnitId,BigDecimal limit,
                    BigDecimal plannedQuantity,String remark,String contractNo) {
    /** 兼容未提供合同号的已有调用方。 */
    public NoticeLine(String id,InboundReceiptReferences.Item item,String baseUnitId,BigDecimal limit,
        BigDecimal plannedQuantity,String remark) {
      this(id,item,baseUnitId,limit,plannedQuantity,remark,null);
    }
  }

  /** @param notice 完整通知内容，明细 ID 保持稳定 @param version 读取时主单版本，从 0 起 */
  record Update(Notice notice,long version) {}
  /** @param context 可信租户成员 @param input 当前版本及完整修改内容 */
  void update(VerifiedTenantContext context,Update input);

  /** @param context 可信租户成员 @param notice 已批准的通知主单和明细 */
  void issue(VerifiedTenantContext context,Notice notice);
}
