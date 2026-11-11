package com.appnexus.contracts.workflow;

import java.util.List;

/** 代码注册的业务配置目录；业务人员只能选择目录项，不能上传脚本、接口地址或字段定义。 */
public record WorkflowBusinessProfile(
    String objectType, String purpose, String nameZh, String nameEn,
    String descriptionZh, String descriptionEn, List<Field> fields, List<Outcome> outcomes,
    String withdrawalCapability) {
  /**
   * 可用于条件及样本的非敏感快照字段。
   * @param key 已声明输出字段，不能读取任意实体属性
   * @param type NUMBER、BOOLEAN 或 TEXT；数值按十进制比较，缺失/非法值失败关闭
   * @param unitZh 中文单位，无单位为空
   * @param unitEn 英文单位，无单位为空
   */
  public record Field(String key, String nameZh, String nameEn, String type,
      String unitZh, String unitEn) {}

  /**
   * 可选结果处理；映射到已注册能力而非浏览器提供的接口地址。
   * @param result APPROVED、REJECTED 或 WITHDRAWN
   * @param capability 执行该结果的稳定能力标识
   */
  public record Outcome(String result, String capability, String nameZh, String nameEn) {}
}
