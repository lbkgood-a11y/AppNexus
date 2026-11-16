package com.appnexus.platform.organization;
import java.util.*;
/** 账号人员执行身份公开契约；仅描述可代表身份，不授予业务操作或数据权限。 */
public interface PersonnelExecution {
  /** 固定选择项；一个人员可有多个组织，选择必须同时明确二者。 */
  record Option(String personId,String personName,String organizationId,String organizationName) {}
  /**
 * @param tenant 可信租户
 * @param member 可信账号成员
 * @return 该账号关联的有效人员及各自有效组织，不合并权限 */
  List<Option> options(UUID tenant,UUID member);
  /**
 * @return 当前关系、人员、账号、组织与任职仍有效才返回 true */
  boolean allowed(UUID tenant,UUID member,UUID person,UUID organization);
  /**
 * @return 指定组织岗位/负责人对应有效账号；人员与账号分别复核，不授予任务之外的业务权限 */
  List<String> resolveAccounts(UUID tenant,UUID organization,String kind,String reference);
 /** 仅在已授权租户成员移除事务中解除账号人员关联；不删除人员、归属或任职。 */
 void unlinkMember(UUID tenant,UUID member,UUID actor);
 /** 复核指定人员的节点岗位/负责人资格，禁止用同账号另一个人员的任职通过。 */
 boolean routeCandidate(UUID tenant,UUID member,UUID person,UUID organization,String kind,String reference);
}
