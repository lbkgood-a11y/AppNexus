package com.appnexus.leave.application;
import java.util.*;
import java.time.LocalDate;
/** 请假固定存取输出端口，限本模块持久化实现，不接收SQL或客户端身份。 */
public interface LeaveRepository {
 /** appliedApprovals固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 List<Map<String,Object>> appliedApprovals(String tenant,String id,String actor);
 /** createDraft固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @param actor 真实登录账号，不替换为被代表人员
  * @param organization 可信有效任职组织，原单据依据锁定后保持
  * @param type PERSONAL、ANNUAL或SICK，原固定请假类型
  * @param start 原首个ISO自然日，不转换浏览器时区
  * @param end 包含当天的末个ISO自然日，不能早于首日
  * @param days 自然日数量1至365，由服务端按首尾日计算
  * @param reason 去首尾空白后1至2000字符，不进入引擎变量
  * @param request 原可信UUID幂等请求键
  * @param person 经验证的被代表人员，不能冒充实际账号
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 int createDraft(String tenant,String id,String actor,String organization,String type,LocalDate start,LocalDate end,int days,String reason,String request,String person);
 /** createdRequest固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param actor 真实登录账号，不替换为被代表人员
  * @param request 原可信UUID幂等请求键
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 Map<String,Object> createdRequest(String tenant,String actor,String request);
 /** ownRequests固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param actor 真实登录账号，不替换为被代表人员
  * @param person 经验证的被代表人员，不能冒充实际账号
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 List<Map<String,Object>> ownRequests(String tenant,String actor,String person);
 /** markSubmitted固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 int markSubmitted(String tenant,String id);
 /** recordSubmission固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @param basis 原非负依据版本，冻结、公共执行及轮次记录同事务
  * @param resolution 原统一对象关联解析标识
  * @param service 原公共解析选定且声明支持的服务类型
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 int recordSubmission(String tenant,String id,long basis,String resolution,String service);
 /** createApprovalCycle固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @param object 当前租户请假父记录标识
  * @param basis 原非负依据版本，冻结、公共执行及轮次记录同事务
  * @param instance 原真实工作流实例标识
  * @param resolution 原统一对象关联解析标识
  * @param configuration 公共解析采用的非负配置版本
  * @param type PERSONAL、ANNUAL或SICK，原固定请假类型
  * @param start 原首个ISO自然日，不转换浏览器时区
  * @param end 包含当天的末个ISO自然日，不能早于首日
  * @param days 自然日数量1至365，由服务端按首尾日计算
  * @param reason 去首尾空白后1至2000字符，不进入引擎变量
  * @param organization 可信有效任职组织，原单据依据锁定后保持
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 int createApprovalCycle(String tenant,String id,String object,long basis,String instance,String resolution,int configuration,String type,String start,String end,int days,String reason,String organization);
 /** bindInstance固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param instance 原真实工作流实例标识
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 int bindInstance(String instance,String tenant,String id);
 /** updateDraft固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param organization 可信有效任职组织，原单据依据锁定后保持
  * @param type PERSONAL、ANNUAL或SICK，原固定请假类型
  * @param start 原首个ISO自然日，不转换浏览器时区
  * @param end 包含当天的末个ISO自然日，不能早于首日
  * @param days 自然日数量1至365，由服务端按首尾日计算
  * @param reason 去首尾空白后1至2000字符，不进入引擎变量
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 int updateDraft(String organization,String type,LocalDate start,LocalDate end,int days,String reason,String tenant,String id);
 /** approvalCycles固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 List<Map<String,Object>> approvalCycles(String tenant,String id,String actor);
 /** request固定存取，业务依据、成员资格、审批轮次与审计受原完整事务约束。
  * @param tenant 可信租户标识，不接受请求体指定
  * @param id 当前租户原记录或服务端新资源UUID
  * @param lock 是否在原完整写事务锁定，不允许客户端解除
  * @return 原固定投影或影响行数，无匹配不得跨租户回退
  */
 List<Map<String,Object>> request(String tenant,String id,boolean lock);
}
