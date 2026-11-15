package com.appnexus.contracts.operation;
import java.util.*;
/** 具体对象的只读诊断扩展；由业务服务授权读取对象，底座不读取业务表或模拟他人登录。 */
public interface ObjectDiagnostics {
 /** 当前认证主体，租户与成员来自可信过滤器，禁止浏览器指定操作者。 */
 record Context(String accountId,String tenantId,String memberId,String requestId) {}
 /** 分层结果；READY/BLOCKED/NOT_APPLICABLE/NOT_CHECKED，不含业务正文及敏感事实。 */
 record Check(String layer,String status,String reason) {}
 /** 当前实现可诊断的已登记操作标识，未知操作不得隐式接受。 */
 Set<String> operations();
 /**
  * 检查当前主体对具体记录的资格，不执行任何业务写入。
  * @param context 服务端认证上下文
  * @param operationId 本提供者明确支持的操作
  * @param objectId 当前租户记录标识，必须先授权读取，拒绝泄露他人记录存在性
  * @return 分层只读结果；存储故障应抛出，不伪装为权限不足
  */
 List<Check> inspect(Context context,String operationId,String objectId);
}
