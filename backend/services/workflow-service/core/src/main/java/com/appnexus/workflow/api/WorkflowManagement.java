package com.appnexus.workflow.api;
import java.util.*;
import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.core.WorkflowModel;
/** 工作流HTTP公开用例契约；禁止向入口泄漏Flowable引擎、Mapper和内部实现。 */
public interface WorkflowManagement {
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 List<WorkflowBusiness.Capability> businesses(WorkflowContext c);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Map<String, Object> configuration(WorkflowContext c);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 List<Map<String, Object>> definitions(WorkflowContext c);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 List<String> validate(WorkflowContext c, WorkflowModel m);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object save(WorkflowContext c, String id, long revision, WorkflowModel m);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object publish(WorkflowContext c, String id, long revision);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object disable(WorkflowContext c, String id, long revision, String reason);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 List<Map<String, Object>> versions(WorkflowContext c, String id);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object start(WorkflowContext c,
      String objectType,
      String objectId,
      long objectVersion,
      String organization,
      String purpose,
      String entry);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 List<Map<String, Object>> tasks(WorkflowContext c, String view);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 List<Map<String, Object>> instances(WorkflowContext c);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Map<String, Object> detail(WorkflowContext c, String id);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Map<String, Object> task(WorkflowContext c, String taskId);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object complete(WorkflowContext c, String taskId, String action, Map<String, String> values);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object complete(WorkflowContext c, String taskId, String action, Map<String, String> values, String comment);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object reassign(WorkflowContext c, String taskId, String actor, String reason);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object retry(WorkflowContext c, String id, String reason);
 /** 工作流公开操作；可信主体、功能权限、实例归属、版本及幂等在原用例逐次复核。 */
 Object withdraw(WorkflowContext c, String id, String reason);
}
