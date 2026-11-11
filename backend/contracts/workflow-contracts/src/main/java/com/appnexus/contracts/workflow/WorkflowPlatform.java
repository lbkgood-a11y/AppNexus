package com.appnexus.contracts.workflow;

import java.util.*;

/** 平台公开能力桥接；每次调用复核有效成员，不以流程分配替代身份与强制权限。 */
public interface WorkflowPlatform {
  /** 配置目录选项只包含当前租户非敏感名称及标识，不包含联系方式、凭据或业务原文。 */
  record Option(String id, String name) {}

  /** 当前租户可选成员、组织及岗位；实现方必须验证流程查看权限并限定租户，不按名称猜选人规则。 */
  default Map<String, List<Option>> configurationOptions(WorkflowContext context) {
    return Map.of("members", List.of(), "organizations", List.of(), "positions", List.of());
  }

  /** 当前成员有效任职的单据组织，供业务表单选择；不默认开放全租户组织目录。 */
  default List<Option> documentOrganizations(WorkflowContext context) { return List.of(); }
  /** 判断当前可信上下文的功能权限；未知权限或无效成员失败关闭。 */
  boolean allowed(WorkflowContext context, String permission);

  /**
   * 解析当前租户内的有效人员，返回账号标识；必须限定组织及有效任职。
   *
   * @param tenantId 租户标识，必填
   * @param organizationId 单据归属组织，组织选人时必填
   * @param kind MEMBER、LEADER 或 POSITION；不按岗位名称推断负责人
   * @param reference 成员账号或岗位标识；LEADER 时可为空
   * @return 当前有效账号集合，无人返回空集合
   */
  List<String> resolve(String tenantId, String organizationId, String kind, String reference);

  /** 复核账号在租户内仍为有效成员；用于处理中授权和人员变动恢复。 */
  boolean active(String tenantId, String accountId);
}
