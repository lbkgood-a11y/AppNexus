package com.appnexus.contracts.document;

import java.util.*;

/** 单据配置公开值对象；不包含业务实体、引擎对象或任意调用地址。 */
public final class Documents {
  private Documents() {}
  /** 可信身份；租户及操作者由认证链建立，requestId 为本次 UUID 请求键。 */
  public record Context(String tenantId, String actorId, String requestId) {}
  /** 条件字段；type 为 NUMBER、BOOLEAN 或 TEXT，unit 为展示单位，不参与隐式换算。 */
  public record Field(String key, String nameZh, String nameEn, String type, String unit) {}
  /** 业务行为声明；operationKey 连接页面/API，permissionCode 仅授权，策略不决定业务目标状态。 */
  public record Action(String actionCode,String nameZh,String nameEn,String permissionCode,
      String operationKey,Set<String> supportedModes) {
    public Action {supportedModes=Set.copyOf(supportedModes);}
  }
  /** 代码注册声明；version 为正整数，entry 为已注册工作流业务能力，行为不在中心执行。 */
  public record Descriptor(String typeCode, String nameZh, String nameEn, String module,
      int version, String entry, String purpose, List<Field> fields, Set<String> outcomes,List<Action> actions) {
    public Descriptor { fields = List.copyOf(fields); outcomes = Set.copyOf(outcomes);actions=List.copyOf(actions); }
    /** 兼容既有纯审批声明；新业务须显式提供行为与权限映射。 */
    public Descriptor(String typeCode,String nameZh,String nameEn,String module,int version,String entry,String purpose,List<Field> fields,Set<String> outcomes) {
      this(typeCode,nameZh,nameEn,module,version,entry,purpose,fields,outcomes,List.of(new Action("submit","提交","Submit","workflow:start",typeCode+".submit",Set.of("APPROVAL"))));
    }
  }
  /** 业务代码提供声明；平台激活和租户授权独立维护。 */
  public interface Provider { Descriptor descriptor(); }
  /** 条件为受控标量比较；同一绑定内条件全部满足才匹配。 */
  public record Condition(String field, String operator, String value) {}
  /** 提交行为策略；APPROVAL 必须指定发布版本，DIRECT 的流程 ID 为空且版本为 0；旧数据缺省 APPROVAL。 */
  public record Binding(String id, String name, String definitionId, int definitionVersion,
      List<Condition> conditions,String actionCode,String mode) {
    public Binding { conditions = List.copyOf(conditions);actionCode=actionCode==null?"submit":actionCode;mode=mode==null?"APPROVAL":mode; }
    /** 旧绑定缺少策略时明确沿用审批，绝不因无流程降级直接提交。 */
    public Binding(String id,String name,String definitionId,int definitionVersion,List<Condition> conditions){this(id,name,definitionId,definitionVersion,conditions,"submit","APPROVAL");}
  }
  /** 固定解析结果；DIRECT 无流程目标且版本为 0，事实仅保留非敏感字段，引用不作为凭证。 */
  public record Resolution(String id, String tenantId, String typeCode, String objectId,
      long basisVersion, int configurationVersion, String definitionId, int definitionVersion,
      Map<String,String> facts,String actionCode,String mode) { public Resolution { facts = Map.copyOf(facts); } }
  /** 公开失败码；调用者按 status 映射协议，禁止回显业务原文。 */
  public static class Rejection extends RuntimeException {
    private final int status;
    public Rejection(int status, String code) { super(code); this.status = status; }
    public int status() { return status; }
  }
}
