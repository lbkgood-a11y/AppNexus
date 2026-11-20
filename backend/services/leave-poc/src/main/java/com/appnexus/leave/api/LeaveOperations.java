package com.appnexus.leave.api;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.Field;
import java.util.*;
/** 请假稳定操作契约，唯一Provider复用；不包含存储或技术运行实现。 */
public final class LeaveOperations {
 private LeaveOperations(){}
  /** 页面提交、业务 API 和工作台共用行为；权限引用不等同于启动流程。 */
  public static final com.appnexus.contracts.businessobject.BusinessObjects.Operation SUBMIT_OPERATION =
      new com.appnexus.contracts.businessobject.BusinessObjects.Operation(
          "submit", "提交", "Submit", "leave.request:submit", "LeaveRequest.submit",
          new com.appnexus.contracts.businessobject.BusinessObjects.PageOperation(
              "workflow/leave", "/workflow/leave", "请假单 POC", "Leave request POC",
              "POST", "/api/leave-poc/{id}/submit"),
          List.of(),
          new InteractionContract("LeaveRequest.submit", 1, "LeaveRequest", "submit",
              "提交", "Submit", InteractionContract.Trigger.USER_ACTION,
              List.of(
                  new InteractionContract.Capability(InteractionContract.Service.BUSINESS,
                      InteractionContract.Completion.SYNCHRONOUS, "LeaveRequest.submit", "",
                      List.of(), Set.of(), "LeaveRequest.submit.data", 1),
                  new InteractionContract.Capability(InteractionContract.Service.WORKFLOW,
                      InteractionContract.Completion.CALLBACK, "leavePoc", "approval",
                      List.of(
                          new Field("days", "请假天数", "Leave days", "NUMBER", "天",true),
                          new Field("requiresHr", "是否超过三天", "More than three days", "BOOLEAN", "",true)),
                      Set.of("APPROVED", "REJECTED", "WITHDRAWN"), "leave.approval", 1))));

  /** 列表和详情打印共享业务动作；字段投影同时用于打印目录，不能独立维护另一份。 */
  public static final com.appnexus.contracts.businessobject.BusinessObjects.Operation PRINT_OPERATION =
      new com.appnexus.contracts.businessobject.BusinessObjects.Operation(
          "print", "打印", "Print", "leave.request:print", "LeaveRequest.print",
          new com.appnexus.contracts.businessobject.BusinessObjects.PageOperation(
              "workflow/leave", "/workflow/leave", "请假列表", "Leave list",
              "POST", "/api/business-object-executions"),
          List.of(new com.appnexus.contracts.businessobject.BusinessObjects.OperationAlias(
              "LeaveRequest.detail.print", new com.appnexus.contracts.businessobject.BusinessObjects.PageOperation(
                  "workflow/leave", "/workflow/leave", "请假详情", "Leave details",
                  "POST", "/api/business-object-executions"))),
          new InteractionContract("LeaveRequest.print", 1, "LeaveRequest", "print",
              "打印", "Print", InteractionContract.Trigger.USER_ACTION,
              List.of(new InteractionContract.Capability(InteractionContract.Service.PRINT,
                  InteractionContract.Completion.CONFIRMATION, "leave.request.v1", "",
                  List.of(
                      new Field("documentNumber", "单据标识", "Document ID", "TEXT", "",true),
                      new Field("startDate", "开始日期", "Start date", "TEXT", "",true),
                      new Field("endDate", "结束日期", "End date", "TEXT", "",true),
                      new Field("days", "自然日天数", "Calendar days", "NUMBER", "天",true),
                      new Field("state", "业务状态", "Business state", "TEXT", "",true)), Set.of(), "leave.request.v1", 1))));

}
