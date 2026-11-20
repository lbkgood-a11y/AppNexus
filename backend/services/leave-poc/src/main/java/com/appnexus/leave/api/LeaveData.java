package com.appnexus.leave.api;
import java.time.LocalDate;
/** 请假固定公开数据，日期为ISO自然日，不携带客户端身份、租户或审批终态。 */
public final class LeaveData {
 private LeaveData(){}
  public record Input(String organizationId, String leaveType, LocalDate startDate,
      LocalDate endDate, String reason) {}
  public record AvailableOperation(String operationCode,String nameZh,String nameEn,String permissionCode,String operationId,boolean allowed,String reasonCode) {}
}
