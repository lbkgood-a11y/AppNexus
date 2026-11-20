package com.appnexus.contracts.businessobject;

import java.util.*;

/** 业务对象配置公开值对象；不包含业务实体、引擎对象或任意调用地址。 */
public final class BusinessObjects {
  private BusinessObjects() {}
  /** 对象业务性质；审批或打印能力不决定对象分类。 */
  public enum Category { MASTER_DATA, TRANSACTION, CONFIGURATION, LEDGER, SYSTEM }

  /**
   * 可信身份，由服务端认证链建立，不能从浏览器自由构造。
   * @param tenantId 当前可信租户标识
   * @param actorId 实际账号标识
   * @param requestId 稳定UUID请求键，同一命令重试保持不变
   * @param personId 已验证的被代表人员；管理调用可为空
   * @param organizationId 已验证的当前业务组织，不替代业务记录归属核验
   */
  public record Context(String tenantId, String actorId, String requestId, String personId, String organizationId) {
    /** 管理调用没有被代表人员，不据此推断业务身份。 */
    public Context(String tenantId,String actorId,String requestId){this(tenantId,actorId,requestId,null,null);}
  }
  /**
   * 数据字段；type 为 NUMBER、BOOLEAN 或 TEXT；unit 不参与隐式换算。
   * @param required 是否必填；必须显式声明，缺失可选字段不自动补值
   */
  public record Field(String key, String nameZh, String nameEn, String type, String unit, boolean required) {
  }
  /** 构建登记的页面操作；入口和 API 只用于解释已接入关系，不允许管理人员填写任意地址。 */
  public record PageOperation(String pageKey,String pagePath,String nameZh,String nameEn,String method,String apiPath) {}
  /** 操作专属审批契约；用途、业务入口及必需终态由业务声明，不能让管理员任意拼接。 */
  public record ApprovalContract(String purpose,String entry,Set<String> outcomes) {
    public ApprovalContract {outcomes=Set.copyOf(outcomes);}
  }
  /** 同一业务动作的页面别名；仅扩展真实入口，不扩展权限或处理器。 */
  public record OperationAlias(String operationId,PageOperation pageOperation) {}
  /** 平台显式关系；只能从构建登记的兼容页面操作中选择，不能改写权限、接口或执行处理器。 */
  public record PageRelationship(String operationCode,String operationId) {}
  /** 可关联候选项，包含真实页面及接口，compatibleOperationCode 为业务允许关联的动作。 */
  public record PageCandidate(String operationId,String compatibleOperationCode,String permissionCode,PageOperation pageOperation) {}
  /** 通用就绪检查；STATIC 表示配置事实，LIVE 表示瞬时依赖，不构成业务执行授权。 */
  public record Check(String operationCode,String category,String code,boolean ready,String target) {}
  /** 业务端可执行性解释，reasonCode 是安全稳定码，不能暴露无权对象的存在性。 */
  public record Availability(String operationCode,String operationId,boolean allowed,String reasonCode) {}
  /** 打印投影契约编码，例如 sales.order.v1；实际数据由业务服务授权提供。 */
  public record PrintingContract(String dataContract) {}
  /** 业务操作只有一份交互声明；页面、权限与驱动能力分离，专业服务不能再登记第二份业务契约。 */
  public record Operation(String operationCode,String nameZh,String nameEn,String permissionCode,
      String operationId,PageOperation pageOperation,List<OperationAlias> aliases,InteractionContract interaction) {
    public Operation {if(!operationId.endsWith("."+operationCode))throw new IllegalArgumentException("BUSINESS_OBJECT_OPERATION_ID_INVALID");Objects.requireNonNull(interaction);aliases=List.copyOf(aliases);if(!interaction.operationCode().equals(operationCode))throw new IllegalArgumentException("INTERACTION_ACTION_MISMATCH");}
    /** 驱动能力由唯一交互声明投影，不保存可漂移的第二份模式集合。 */
    public Set<String> supportedServices(){return interaction.capabilities().stream().map(c->switch(c.service()){case BUSINESS->"BUSINESS";case WORKFLOW->"WORKFLOW";case PRINT->"PRINT";case MESSAGE->"MESSAGE";}).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    /** 审批专属读取视图，不接受缺省契约或从其他服务猜测用途。 */
    public ApprovalContract approval(){return interaction.capabilities().stream().filter(c->c.service()==InteractionContract.Service.WORKFLOW).findFirst().map(c->new ApprovalContract(c.purpose(),c.reference(),c.outcomes())).orElse(null);}
    /** 打印专属读取视图，同一声明为场景兼容校验提供权威契约编码。 */
    public PrintingContract printing(){return interaction.capabilities().stream().filter(c->c.service()==InteractionContract.Service.PRINT).findFirst().map(c->new PrintingContract(c.dataContract())).orElse(null);}
  }
  /**
   * 统一对象声明的数据权限部分；不包含对象身份、版本或权限码的第二份定义。
   * @param scope TENANT或PLATFORM管理域，必填
   * @param resources 首项为主资源，子资源继承主资源行权限；资源内字段键必须唯一
   * @param operationScopes 已声明operationCode到受控范围编码集合；无配置则不启用该操作行范围
   * @param dimensions 已接通的组合授权条件维度，缺省为空；SQL映射由业务存储代码固定
   */
  public record DataAccess(String scope,List<DataResource> resources,Map<String,Set<String>> operationScopes,List<DataDimension> dimensions) {
    /** 既有对象缺省不开放条件维度，保持已声明的行范围。 */
    public DataAccess(String scope,List<DataResource> resources,Map<String,Set<String>> operationScopes){this(scope,resources,operationScopes,List.of());}
    public DataAccess {
      dimensions=List.copyOf(dimensions);
      Objects.requireNonNull(scope);resources=List.copyOf(resources);
      var copy=new TreeMap<String,Set<String>>();operationScopes.forEach((key,value)->copy.put(key,Set.copyOf(value)));operationScopes=Map.copyOf(copy);
    }
  }
  /**
   * 对象可授权条件维度；不包含 SQL 映射，只有真实执行支持的字段才可声明。
   * @param field 稳定业务键，字母开头的字母数字
   * @param nameZh 中文名称
   * @param nameEn 英文名称
   * @param valueType UUID 或 TEXT；UUID 值必须是规范标识
   * @param operators 已实现的 EQ/IN 集合
   */
  public record DataDimension(String field,String nameZh,String nameEn,String valueType,Set<String> operators) {
    public DataDimension {operators=Set.copyOf(operators);}
  }
  /**
   * 主从数据资源声明；不得携带数据库表名、列名或SQL。
   * @param resource 全局唯一小写点分资源键
   * @param parentResource 主资源为空串，子资源为同对象主资源键
   * @param nameZh 中文名称，必填
   * @param nameEn 英文名称，必填
   * @param responseType 自动响应投影的完整record类名；缺省空串表示由业务执行器显式保护，不接受客户端设置
   * @param fields 已接通执行的字段保护能力
   */
  public record DataResource(String resource,String parentResource,String nameZh,String nameEn,List<DataField> fields,String responseType) {
    /** 显式业务执行器无需自动响应绑定，缺省保持空串。 */
    public DataResource(String resource,String parentResource,String nameZh,String nameEn,List<DataField> fields){this(resource,parentResource,nameZh,nameEn,fields,"");}
    public DataResource {fields=List.copyOf(fields);Objects.requireNonNull(responseType);}
  }
  /**
   * 字段安全能力，表示业务实际支持的配置，不直接授予访问权限。
   * @param field 资源内稳定业务字段键，禁止自由属性路径
   * @param nameZh 中文名称，必填
   * @param nameEn 英文名称，必填
   * @param kind 受控语义算法编码；由授权目录校验PHONE/EMAIL等实际支持类型
   * @param modes 已实现的READ_ONLY/MASKED/HIDDEN集合，不支持的模式不得声明
   */
  public record DataField(String field,String nameZh,String nameEn,String kind,Set<String> modes) {
    public DataField {modes=Set.copyOf(modes);}
  }
  /**
   * 业务对象唯一代码声明；目录、操作、服务能力和数据权限均由此投影，业务仍在原执行器处理。
   * @param objectType 全局稳定对象标识，不能重复登记
   * @param nameZh 中文对象名称
   * @param nameEn 英文对象名称
   * @param module 所属业务模块标识
   * @param version 正整数契约版本；改变安全语义必须递增并迁移策略
   * @param category 对象业务分类，必填
   * @param fields 交互使用的非敏感业务事实字段，不等于完整数据库字段
   * @param operations 对象内唯一操作清单；Controller只能引用
   * @param codingFields 已接通编码字段，缺省为空；不改变原手工录入和业务唯一约束
   * @param dataAccess 可空表示尚未接入数据权限，不推断为ALL；非空必须引用本对象操作
   */
  public record Descriptor(String objectType, String nameZh, String nameEn, String module,
      int version, Category category, List<Field> fields,List<Operation> operations,DataAccess dataAccess,List<CodingField> codingFields) {
    /** 已有声明缺省不开启编码协助，避免向未实现字段提供发号入口。 */
    public Descriptor(String objectType,String nameZh,String nameEn,String module,int version,Category category,List<Field> fields,List<Operation> operations,DataAccess dataAccess){this(objectType,nameZh,nameEn,module,version,category,fields,operations,dataAccess,List.of());}
    /** 尚未接入数据权限的既有对象保持空声明，不生成虚假范围能力。 */
    public Descriptor(String objectType,String nameZh,String nameEn,String module,int version,Category category,List<Field> fields,List<Operation> operations){this(objectType,nameZh,nameEn,module,version,category,fields,operations,null);}
    public Descriptor { Objects.requireNonNull(category,"BUSINESS_OBJECT_CATEGORY_REQUIRED");
      for(var operation:operations)if(!operation.operationId().equals(objectType+"."+operation.operationCode()))throw new IllegalArgumentException("BUSINESS_OBJECT_OPERATION_ID_INVALID");
      fields = List.copyOf(fields); operations=List.copyOf(operations);codingFields=List.copyOf(codingFields);
      var operationCodes=operations.stream().map(Operation::operationCode).collect(java.util.stream.Collectors.toSet());
      if(codingFields.stream().map(CodingField::field).distinct().count()!=codingFields.size()||codingFields.stream().anyMatch(f->!operationCodes.contains(f.operationCode())))throw new IllegalArgumentException("CODING_FIELD_OPERATION_INVALID");
      if(dataAccess!=null){
        if(!Set.of("TENANT","PLATFORM").contains(dataAccess.scope())||dataAccess.resources().isEmpty())throw new IllegalArgumentException("BUSINESS_OBJECT_DATA_ACCESS_INVALID");
        var codes=operations.stream().map(Operation::operationCode).collect(java.util.stream.Collectors.toSet());
        if(!codes.containsAll(dataAccess.operationScopes().keySet()))throw new IllegalArgumentException("BUSINESS_OBJECT_DATA_OPERATION_UNDECLARED");
      }
    }

  }
  /**
   * 业务实际接通的编码字段；管理人员不能配置任意属性或调用地址。
   * @param field 业务字段键，必填；当前仅允许字母开头的字母数字键
   * @param operationCode 发号所需的本对象操作，必填；仍由业务保存接口复核数据权限
   * @param maxLength 编码最大字符数，1–128；与业务字段校验保持一致
   */
  public record CodingField(String field,String operationCode,int maxLength) {
    public CodingField {if(field==null||!field.matches("[a-zA-Z][a-zA-Z0-9]*")||maxLength<1||maxLength>128)throw new IllegalArgumentException("CODING_FIELD_INVALID");}
  }
  /** 业务代码提供声明；平台激活和租户授权独立维护。 */
  public interface Provider {
    Descriptor descriptor();
    /** 对象只接入一次运行端口，所有通用能力复用；纯目录声明不伪造执行实现。 */
    default BusinessObjectRuntime runtime() { return this instanceof BusinessObjectRuntime runtime ? runtime : null; }
  }
  /** 条件为受控标量比较；同一绑定内条件全部满足才匹配。 */
  public record Condition(String field, String operator, String value) {}
  /** 业务对象操作策略；WORKFLOW 必须指定发布版本，BUSINESS 无流程；操作、服务和页面标识均须显式提供。 */
  public record Binding(String id, String name, String definitionId, int definitionVersion,
      List<Condition> conditions,String operationCode,String service,String operationId,String sceneCode) {
    public Binding { conditions = List.copyOf(conditions);Objects.requireNonNull(operationCode,"INTERACTION_ACTION_REQUIRED");Objects.requireNonNull(service,"INTERACTION_SERVICE_REQUIRED");Objects.requireNonNull(operationId,"INTERACTION_OPERATION_REQUIRED"); }

  }
  /** 固定解析结果；BUSINESS 无流程目标且版本为 0，事实仅保留非敏感字段，引用不作为凭证。 */
  public record Resolution(String id, String tenantId, String objectType, String objectId,
      long basisVersion, int configurationVersion, String definitionId, int definitionVersion,
      Map<String,String> facts,String operationCode,String service,String sceneCode,String dataContract) { public Resolution { facts = Map.copyOf(facts); } }
  /** 公开失败码；调用者按 status 映射协议，禁止回显业务原文。 */
  public static class Rejection extends RuntimeException {
    private final int status;
    public Rejection(int status, String code) { super(code); this.status = status; }
    public int status() { return status; }
  }
}
