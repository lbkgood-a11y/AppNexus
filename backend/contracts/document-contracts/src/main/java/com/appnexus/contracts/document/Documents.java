package com.appnexus.contracts.document;

import java.util.*;

/** 单据配置公开值对象；不包含业务实体、引擎对象或任意调用地址。 */
public final class Documents {
  private Documents() {}
  /** 可信身份；租户及操作者由认证链建立，requestId 为本次 UUID 请求键。 */
  public record Context(String tenantId, String actorId, String requestId) {}
  /**
   * 数据字段；type 为 NUMBER、BOOLEAN 或 TEXT；unit 不参与隐式换算。
   * @param required 是否必填；五参数构造默认 true，缺失可选字段不自动补值
   */
  public record Field(String key, String nameZh, String nameEn, String type, String unit, boolean required) {
    /** 已有业务字段默认必填，保持原审批依据完整性。 */
    public Field(String key,String nameZh,String nameEn,String type,String unit){this(key,nameZh,nameEn,type,unit,true);}
  }
  /** 构建登记的页面操作；入口和 API 只用于解释已接入关系，不允许管理人员填写任意地址。 */
  public record PageOperation(String pageKey,String pagePath,String nameZh,String nameEn,String method,String apiPath) {}
  /** 行为专属审批契约；用途、业务入口及必需终态由业务声明，不能让管理员任意拼接。 */
  public record ApprovalContract(String purpose,String entry,Set<String> outcomes) {
    public ApprovalContract {outcomes=Set.copyOf(outcomes);}
  }
  /** 同一业务动作的页面别名；仅扩展真实入口，不扩展权限或处理器。 */
  public record OperationAlias(String operationKey,PageOperation pageOperation) {}
  /** 平台显式关系；只能从构建登记的兼容页面操作中选择，不能改写权限、接口或执行处理器。 */
  public record PageRelationship(String actionCode,String operationKey) {}
  /** 可关联候选项，包含真实页面及接口，compatibleActionCode 为业务允许关联的动作。 */
  public record PageCandidate(String operationKey,String compatibleActionCode,String permissionCode,PageOperation pageOperation) {}
  /** 通用就绪检查；STATIC 表示配置事实，LIVE 表示瞬时依赖，不构成业务执行授权。 */
  public record Check(String actionCode,String category,String code,boolean ready,String target) {}
  /** 业务端可执行性解释，reasonCode 是安全稳定码，不能暴露无权对象的存在性。 */
  public record Availability(String actionCode,String operationKey,boolean allowed,String reasonCode) {}
  /** 打印投影契约编码，例如 sales.order.v1；实际数据由业务服务授权提供。 */
  public record PrintingContract(String dataContract) {}
  /** 业务行为只有一份交互声明；页面、权限与驱动能力分离，专业服务不能再登记第二份业务契约。 */
  public record Action(String actionCode,String nameZh,String nameEn,String permissionCode,
      String operationKey,PageOperation pageOperation,List<OperationAlias> aliases,InteractionContract interaction) {
    public Action {Objects.requireNonNull(interaction);aliases=List.copyOf(aliases);if(!interaction.actionCode().equals(actionCode))throw new IllegalArgumentException("INTERACTION_ACTION_MISMATCH");}
    /** 驱动能力由唯一交互声明投影，不保存可漂移的第二份模式集合。 */
    public Set<String> supportedServices(){return interaction.capabilities().stream().map(c->switch(c.service()){case BUSINESS->"BUSINESS";case WORKFLOW->"WORKFLOW";case PRINT->"PRINT";case MESSAGE->"MESSAGE";}).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    /** 审批专属读取视图，不接受缺省契约或从其他服务猜测用途。 */
    public ApprovalContract approval(){return interaction.capabilities().stream().filter(c->c.service()==InteractionContract.Service.WORKFLOW).findFirst().map(c->new ApprovalContract(c.purpose(),c.reference(),c.outcomes())).orElse(null);}
    /** 打印专属读取视图，同一声明为场景兼容校验提供权威契约编码。 */
    public PrintingContract printing(){return interaction.capabilities().stream().filter(c->c.service()==InteractionContract.Service.PRINT).findFirst().map(c->new PrintingContract(c.dataContract())).orElse(null);}
  }
  /** 代码注册声明；version 为正整数，行为交互契约是服务能力的唯一来源，行为不在中心执行。 */
  public record Descriptor(String typeCode, String nameZh, String nameEn, String module,
      int version, List<Field> fields,List<Action> actions) {
    public Descriptor { fields = List.copyOf(fields); actions=List.copyOf(actions); }

  }
  /** 业务代码提供声明；平台激活和租户授权独立维护。 */
  public interface Provider { Descriptor descriptor(); }
  /** 条件为受控标量比较；同一绑定内条件全部满足才匹配。 */
  public record Condition(String field, String operator, String value) {}
  /** 单据行为策略；WORKFLOW 必须指定发布版本，BUSINESS 无流程；行为、服务和页面标识均须显式提供。 */
  public record Binding(String id, String name, String definitionId, int definitionVersion,
      List<Condition> conditions,String actionCode,String service,String operationKey,String sceneCode) {
    public Binding { conditions = List.copyOf(conditions);Objects.requireNonNull(actionCode,"INTERACTION_ACTION_REQUIRED");Objects.requireNonNull(service,"INTERACTION_SERVICE_REQUIRED");Objects.requireNonNull(operationKey,"INTERACTION_OPERATION_REQUIRED"); }

  }
  /** 固定解析结果；BUSINESS 无流程目标且版本为 0，事实仅保留非敏感字段，引用不作为凭证。 */
  public record Resolution(String id, String tenantId, String typeCode, String objectId,
      long basisVersion, int configurationVersion, String definitionId, int definitionVersion,
      Map<String,String> facts,String actionCode,String service,String sceneCode,String dataContract) { public Resolution { facts = Map.copyOf(facts); } }
  /** 公开失败码；调用者按 status 映射协议，禁止回显业务原文。 */
  public static class Rejection extends RuntimeException {
    private final int status;
    public Rejection(int status, String code) { super(code); this.status = status; }
    public int status() { return status; }
  }
}
