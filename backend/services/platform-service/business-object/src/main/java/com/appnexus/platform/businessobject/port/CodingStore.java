package com.appnexus.platform.businessobject.port;

import com.appnexus.contracts.businessobject.BusinessCoding.Rule;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * 编码用例拥有的持久化端口；不接受 SQL、表名或客户端审计时间。
 * 所有方法必须限定 Context 中的可信租户；锁、流水与回执必须使用同一连接和事务。
 */
public interface CodingStore {
  /**
   * 已锁定的编码器，不接受客户端状态替代数据库事实。
   * @param field 对象声明中的编码字段
   * @param rule 数据库保存的规则，发号前仍须按当前字段约束验证
   * @param enabled 是否允许发号；停用后也不得重放历史结果
   * @param revision 非负配置版本，用于保存时的乐观版本检查
   */
  record Encoder(String field, Rule rule, boolean enabled, long revision) {}
  /**
   * 同租户同账号请求的历史回执；重放仍须重新鉴权及检查编码器启用状态。
   * @param objectType 原发号对象，必须与重放目标一致
   * @param encoderKey 原编码器键，跨编码器复用请求必须拒绝
   * @param code 原子保留的编码，不因重放再次消耗流水
   */
  record Issued(String objectType, String encoderKey, String code) {}
  /**
   * 原子执行保存或发号；任何异常均回滚正文、流水及审计。
   * @param work 事务内用例，禁止异步逃逸当前事务
   * @param conflictCode 唯一约束冲突时映射的稳定业务错误码
   * @return 用例结果；提交成功才对外返回
   * @throws com.appnexus.contracts.businessobject.BusinessObjects.Rejection 唯一约束冲突
   */
  <T> T atomic(Supplier<T> work, String conflictCode);
  /** 返回当前租户配置与只读审计投影；不修改持久化状态。 */
  List<Map<String,Object>> configurations(Context context, String objectType);
  /** 返回启用的字段编码器候选；调用方先检查目标业务操作权限。 */
  List<Map<String,Object>> options(Context context, String objectType, String field);
  /** 在 atomic 内锁定当前租户编码器；不存在返回 null，不创建占位记录。 */
  Encoder lock(Context context, String objectType, String encoderKey);
  /** 当前编码器是否已经产生回执；仅在编码器锁内判断规则冻结。 */
  boolean hasIssues(Context context, String objectType, String encoderKey);
  /** 新增或更新配置；操作者来自 Context，创建信息不允许被更新覆盖。 */
  void save(Context context, String objectType, String encoderKey, String field, String name, boolean enabled, Rule rule, boolean create);
  /** 按可信租户、账号和请求 UUID 读取幂等回执，不自动授予重放权限。 */
  Issued issued(Context context);
  /** 数据库 UTC 时间点；一次发号只读取一次，避免跨午夜日期与周期不一致。 */
  Instant now();
  /** 在同一事务锁定周期流水；首次初始化为 0，返回尚未消耗的当前值。 */
  long sequence(Context context, String objectType, String encoderKey, String period);
  /** 同一事务推进流水并写入唯一回执；失败必须回滚流水与审计。 */
  void record(Context context, String objectType, String encoderKey, String field, String period, long sequence, String code);
}
