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
   * @param rule 完整编码规则，已发号后的字段与规则冻结
   */
  record Input(long revision, String field, String name, boolean enabled, Rule rule) {}

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
