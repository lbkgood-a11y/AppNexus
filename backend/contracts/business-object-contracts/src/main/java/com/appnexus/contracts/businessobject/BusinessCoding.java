package com.appnexus.contracts.businessobject;

import java.util.List;

/** 通用编码公开端口；只发放编码字符串，不写业务正文；调用者仍执行业务保存授权与唯一约束。 */
public interface BusinessCoding {
  /**
   * 发放已配置编码；事务提交即消耗号码，取消录入也不回收，允许断号。
   * @param context 可信会话上下文；requestId 为 UUID 幂等键，同账号同租户同键返回原结果
   * @param objectType 已通过同一 Provider 声明的对象类型
   * @param encoderKey 对象内编码器键，必填；不会推断默认编码器
   * @return 原子保留的编码；跨对象复用请求键拒绝
   * @throws BusinessObjects.Rejection 无权限、未配置、停用、规则冲突或流水耗尽
   */
  String issue(BusinessObjects.Context context,String objectType,String encoderKey);

  /**
   * 有序编码段；固定文本、服务端日期和流水三类，禁止脚本及客户端日期。
   * @param kind TEXT、DATE、SEQUENCE；每条规则恰好一段 SEQUENCE
   * @param value TEXT 为1–32位字母数字或._-；DATE 为 yyyyMMdd、yyyyMM 或 yyyy；SEQUENCE 为空串
   * @param width SEQUENCE 补零位数1–12；其他类型必须为0，溢出拒绝
   */
  record Segment(String kind,String value,int width) {}
  /**
   * 编码规则值；由独立规则目录保存不可变版本，对象绑定显式引用，避免修改历史语义。
   * @param segments 1–10个有序分段，输出首字符必须为字母或数字
   * @param reset NEVER、YEAR、MONTH、DAY；默认由界面显式提交 NEVER，周期必须有对应精度日期段
   * @param zoneId 必填 IANA 时区，如 Asia/Shanghai；只用于日期和周期计算，审计时间仍为 UTC
   */
  record Rule(List<Segment> segments,String reset,String zoneId) {
    public Rule {segments=List.copyOf(segments);}
  }
}
