package com.appnexus.masterdata.application.product;
import java.util.*;
/** 商品审批固定仓储输出端口，申请、父冻结及执行绑定参与原完整事务，不接受SQL或客户端身份。 */
public interface ProductApprovalRepository {
 /** lockProduct固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 Map<String,Object> lockProduct(String tenant,String product);
 /** existingRequest固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param applicant 真实申请账号UUID文本，必填；与可信执行账号一致
  * @param requestKey 已校验的规范UUID幂等键，同租户、账号及操作内唯一
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> existingRequest(String tenant,String applicant,String requestKey);
 /** freezeProductVersion固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int freezeProductVersion(String actor,String tenant,String product);
 /** createApproval固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param requestKey 已校验的规范UUID幂等键，同租户、账号及操作内唯一
  * @param productVersion 冻结或结果商品版本，非负；在同一聚合事务内确定
  * @param applicant 真实申请账号UUID文本，必填；与可信执行账号一致
  * @param organization 已授权当前租户启用组织UUID；新建必填，原更新null保留归属
  * @param snapshot 服务端重读并编码的固定审批快照JSON，不能来自客户端事实
  * @param creator 原可信创建账号UUID文本；不允许客户端指定或覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，与业务写入同事务
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int createApproval(String id,String tenant,String product,String requestKey,long productVersion,String applicant,String organization,String snapshot,String creator,String updater);
 /** bindExecution固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param instance 公共执行器返回的可信流程实例标识，不接受客户端回传
  * @param resolution 公共对象关联解析标识，来自原统一绑定结果
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int bindExecution(String instance,String resolution,String tenant,String id);
 /** approvalHistory固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> approvalHistory(String tenant,String product);
 /** approvalDetail固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> approvalDetail(String tenant,String id);
}
