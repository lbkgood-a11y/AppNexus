package com.appnexus.platform.authorization;
import java.util.*;
import java.util.function.Predicate;
/** 操作权限表达式唯一计算实现；只判断功能权限，不替代对象或业务状态检查。 */
public final class OperationPermissionExpression {
 private OperationPermissionExpression(){}
 /**
  * 计算 OR-of-AND；空表达式默认拒绝，不把未登记权限解释成公开访问。
  * @param groups 每项为 & 连接的 AND 组，多项之间 OR；只允许已校验的稳定权限码
  * @param permission 当前主体权限判定，不得跨账号或租户复用结果
  * @return 任一完整组满足时为 true
  */
 public static boolean allows(List<String> groups,Predicate<String> permission){
  Objects.requireNonNull(groups);Objects.requireNonNull(permission);
  return !groups.isEmpty()&&groups.stream().anyMatch(group->!group.isBlank()&&Arrays.stream(group.split("&",-1)).allMatch(code->!code.isBlank()&&permission.test(code)));
 }
}
