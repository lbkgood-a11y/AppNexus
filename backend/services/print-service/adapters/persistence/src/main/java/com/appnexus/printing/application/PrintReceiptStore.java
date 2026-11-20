package com.appnexus.printing.application;

/** 打印回执输出端口；调用者必须持有完整回执用例事务，存储实现不独立提交。 */
public interface PrintReceiptStore {
  /** @param state 锁定的当前状态 @param agent 原领取代理 @param expired 数据库 UTC 租约判断 @param sameResult 原回执与本次结果 JSON 结构是否一致 */
  record Current(String state, String agent, boolean expired, boolean sameResult) {}
  /** @param tenant 可信租户 UUID @param id 任务 UUID @param result 已验证的非敏感 JSON @return 锁定事实，不存在抛原稳定业务异常；不能先跨租户查询存在性 */
  Current lock(String tenant, String id, String result);
  /** @param tenant 可信租户 UUID @param id 已锁任务 UUID @param state 应用及领域规则允许的目标状态 @param result 已验证非敏感 JSON；更新与任务审计同事务 */
  void update(String tenant, String id, String state, String result);
}
