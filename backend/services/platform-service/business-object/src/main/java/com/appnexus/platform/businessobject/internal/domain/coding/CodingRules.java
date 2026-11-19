package com.appnexus.platform.businessobject.internal.domain.coding;

import com.appnexus.contracts.businessobject.BusinessCoding.Rule;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 编码领域规则；只依赖日期和值对象，不读取时钟、数据库、会话或框架。 */
public final class CodingRules {
  private CodingRules() {}
  /**
   * 按服务端数据库时间确定流水周期，日期与编码输出使用同一时间点。
   * @param rule 已验证的规则；zoneId 仅影响业务日期，不改变 UTC 审计时间
   * @param date 规则时区中的业务自然日
   * @return NEVER 返回 ALL，其他周期返回对应精度的日期键
   */
  public static String period(Rule rule, LocalDate date) {
    return switch(rule.reset()) {case "DAY" -> date.format(DateTimeFormatter.BASIC_ISO_DATE);case "MONTH" -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));case "YEAR" -> date.format(DateTimeFormatter.ofPattern("yyyy"));default -> "ALL";};
  }
  /**
   * 校验日期精度、段数及长度；周期必须出现在输出中，避免重置后生成相同编码。
   * @param rule 必填规则；分段、重置周期与时区约束遵循公开 Rule 契约
   * @param maxLength 对象字段允许的最大输出字符数，来自构建内 CodingField 声明
   * @throws Rejection 规则无效或最大长度不足，返回 CODING_RULE_INVALID
   */
  public static void validate(Rule rule,int maxLength){
    Objects.requireNonNull(rule);ZoneId.of(rule.zoneId());
    if(!Set.of("NEVER","YEAR","MONTH","DAY").contains(rule.reset())||rule.segments().isEmpty()||rule.segments().size()>10)fail(400,"CODING_RULE_INVALID");
    int sequence=0,datePrecision=0,length=0;
    for(var segment:rule.segments()){
      switch(segment.kind()){
        case "TEXT"->{if(segment.value()==null||!segment.value().matches("[A-Za-z0-9._-]{1,32}")||segment.width()!=0)fail(400,"CODING_RULE_INVALID");length+=segment.value().length();}
        case "DATE"->{if(!Set.of("yyyyMMdd","yyyyMM","yyyy").contains(segment.value())||segment.width()!=0)fail(400,"CODING_RULE_INVALID");datePrecision=Math.max(datePrecision,segment.value().length());length+=segment.value().length();}
        case "SEQUENCE"->{if(!"".equals(segment.value())||segment.width()<1||segment.width()>12)fail(400,"CODING_RULE_INVALID");sequence++;length+=segment.width();}
        default->fail(400,"CODING_RULE_INVALID");
      }
    }
    int required=switch(rule.reset()){case "DAY"->8;case "MONTH"->6;case "YEAR"->4;default->0;};
    if(sequence!=1||datePrecision<required||length>maxLength)fail(400,"CODING_RULE_INVALID");
    render(rule,LocalDate.of(2026,11,9),1);
  }
  /**
   * 使用固定格式生成字符串；数值耗尽直接拒绝，绝不截断或循环回到1。
   * @param rule 已验证的有序编码规则
   * @param date 规则时区中的数据库业务日期；不得由客户端提供
   * @param sequence 当前事务预留的流水，必须大于0且不超过流水段宽度
   * @return 完整编码文本，补零不改变流水数值
   * @throws Rejection 流水耗尽或规则无法生成合法编码
   */
  public static String render(Rule rule,LocalDate date,long sequence){
    var result=new StringBuilder();
    for(var segment:rule.segments())switch(segment.kind()){
      case "TEXT"->result.append(segment.value());
      case "DATE"->result.append(date.format(DateTimeFormatter.ofPattern(segment.value(),Locale.ROOT)));
      case "SEQUENCE"->{String number=Long.toString(sequence);if(sequence<1||number.length()>segment.width())fail(409,"CODING_SEQUENCE_EXHAUSTED");result.append("0".repeat(segment.width()-number.length())).append(number);}
      default->fail(400,"CODING_RULE_INVALID");
    }
    if(!result.toString().matches("[A-Za-z0-9][A-Za-z0-9._-]*"))fail(400,"CODING_RULE_INVALID");return result.toString();
  }
  private static void fail(int status,String code){throw new Rejection(status,code);}
}
