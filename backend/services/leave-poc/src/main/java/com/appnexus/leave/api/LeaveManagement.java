package com.appnexus.leave.api;
import java.util.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.contracts.workflow.WorkflowPlatform;
import com.appnexus.leave.api.LeaveData.*;
/** 请假公开用例，不向HTTP入口暴露存储或技术装配。 */
public interface LeaveManagement {
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 List<AvailableOperation> actions(WorkflowContext c);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 List<Map<String,Object>> timeline(WorkflowContext c,String id);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 List<WorkflowPlatform.Option> organizations(WorkflowContext c);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 Map<String, Object> create(WorkflowContext c, Input input);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 List<Map<String, Object>> list(WorkflowContext c);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 Map<String, Object> read(WorkflowContext c, String id, String taskId);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 Map<String, Object> submit(WorkflowContext c, String id);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 Map<String,Object> update(WorkflowContext c,String id,long expectedBasisVersion,Input input);
 /** 请假原公开操作；主体、租户、冻结依据与任务资格由真实用例复核。 */
 List<Map<String,Object>> cycles(WorkflowContext c,String id);
}
