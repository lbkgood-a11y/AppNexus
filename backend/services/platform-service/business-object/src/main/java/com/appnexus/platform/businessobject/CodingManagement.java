package com.appnexus.platform.businessobject;

import com.appnexus.contracts.businessobject.BusinessCoding;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import java.util.List;
import java.util.Map;

/** 编码配置与协助录入的公开输入端口；配置管理权与业务发号权分别复核。 */
public interface CodingManagement extends BusinessCoding {
  /**
   * 编码配置写入契约，不接受客户端操作者、租户或审计字段。
   * @param revision 首次为 -1，修改回传非负版本；冲突禁止覆盖
   * @param field 已声明的文本编码字段
   * @param name 显示名称，去除首尾空格后 1–100 字符
   * @param enabled 是否允许新发号；停用后幂等重放也必须拒绝
   * @param ruleKey 独立规则标识，必填；只允许引用当前租户规则
   * @param ruleVersion 固定规则版本，大于0；不会自动跟随最新版本
   */
  record Input(long revision, String field, String name, boolean enabled, String ruleKey, long ruleVersion) {}

  /**
   * 新规则版本输入；已存在版本不可原地覆盖。
   * @param revision 最新版本号；首次为-1，已有规则每次追加版本必须回传最新版本
   * @param name 显示名称，去空格后1–100字符
   * @param rule 分段规则，最大输出128字符
   */
  record RuleInput(long revision, String name, Rule rule) {}
  /**
   * 读取当前租户全部规则版本；需要独立规则管理权限，不授予业务发号权。
   * @param context 可信会话账号、租户和请求上下文
   * @return 按规则键和版本倒序返回的不可变版本及只读审计
   * @throws com.appnexus.contracts.businessobject.BusinessObjects.Rejection 无规则管理权限
   */
  List<Map<String,Object>> rules(Context context);
  /**
   * 对象绑定候选；先复核目标对象配置权，不要求独立规则维护权。
   * @param context 可信会话上下文
   * @param type 代码声明且已向本租户开放的对象类型
   * @return 当前租户可绑定的精确规则版本，不消耗流水
   */
  List<Map<String,Object>> ruleOptions(Context context, String type);
  /**
   * 原子追加不可变版本；冲突不推进版本或审计。
   * @param context 可信会话上下文，账号和UTC时间不接受客户端指定
   * @param key 1–40字符，字母开头，仅字母数字及下划线，租户内唯一
   * @param input 新版本内容及读取时的最新版本；首次revision为-1
   * @throws com.appnexus.contracts.businessobject.BusinessObjects.Rejection 无权限、非法规则或版本冲突
   */
  void saveRule(Context context, String key, RuleInput input);

  /**
   * 读取租户编码配置，先校验配置管理权及对象授权。
   * @param context 可信租户和账号上下文
   * @param type 已声明对象编码
   * @return 当前授权范围内编码器配置
   */
  List<Map<String, Object>> configurations(Context context, String type);

  /**
   * 按目标业务操作授权读取可用编码器，不要求配置管理权限。
   * @param context 可信租户和账号上下文
   * @param type 已声明对象编码
   * @param field 已声明的编码字段
   * @return 当前允许协助录入的候选，不触发发号
   */
  List<Map<String, Object>> options(Context context, String type, String field);

  /**
   * 原子保存配置及记录审计，已发号后冻结字段和规则。
   * @param context 可信租户和账号上下文
   * @param type 已声明对象编码
   * @param key 对象内编码器键，1–40 字符，字母开头，仅字母数字和下划线
   * @param input 已验证配置输入，版本冲突不得覆盖
   */
  void save(Context context, String type, String key, Input input);
}
