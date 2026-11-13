package com.appnexus.contracts.document;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 通用服务交互的唯一声明；专业服务仅从能力投影读取元数据，不自行登记业务契约。
 *
 * @param code 稳定契约编码，必填；不能以页面别名替代
 * @param version 正整数版本；改变输入或结果语义时必须升级
 * @param objectType 业务对象类型，必须等于所属单据类型
 * @param actionCode 所属业务行为编码，不能以权限码代替
 * @param nameZh 中文名称
 * @param nameEn 英文名称
 * @param trigger 触发来源；声明本身不创建事件订阅或调度任务
 * @param capabilities 允许选择的目标服务及专业协议；同一服务只能声明一次
 */
public record InteractionContract(
    String code, int version, String objectType, String actionCode,
    String nameZh, String nameEn, Trigger trigger, List<Capability> capabilities) {
  /** 构建时拒绝不完整或重复声明，避免运行时选择隐式默认服务。 */
  public InteractionContract {
    if (code == null || code.isBlank() || objectType == null || objectType.isBlank()
        || actionCode == null || actionCode.isBlank() || version < 1) {
      throw new IllegalArgumentException("INTERACTION_CONTRACT_INVALID");
    }
    Objects.requireNonNull(trigger);
    capabilities = List.copyOf(capabilities);
    if (capabilities.isEmpty()
        || capabilities.stream().map(Capability::service).distinct().count() != capabilities.size()) {
      throw new IllegalArgumentException("INTERACTION_CAPABILITY_INVALID");
    }
  }

  /** 触发来源独立于目标服务；新增类型仍需实现对应驱动器才能执行。 */
  public enum Trigger { USER_ACTION, BUSINESS_EVENT, SERVICE_RESULT, SCHEDULE }

  /** MESSAGE 仅为协议扩展点；未部署执行器时禁止开放绑定。 */
  public enum Service { BUSINESS, WORKFLOW, PRINT, MESSAGE }

  /** 完成约定；创建任务成功不能解释为最终业务完成。 */
  public enum Completion { SYNCHRONOUS, CALLBACK, CONFIRMATION, ASYNCHRONOUS }

  /**
   * 专业服务协议投影，字段与结果只属于当前服务。
   *
   * @param service 目标服务类型
   * @param completion 完成约定；当前业务同步完成、审批结果回调、打印结果确认
   * @param reference 已注册业务能力或打印数据契约编码，必填
   * @param purpose 服务用途；工作流必须提供明确用途以隔离不同审批语义
   * @param fields 非敏感输入字段元数据，同一投影内键唯一；不包含真实单据值
   * @param dataContract 数据契约稳定编码，不是执行处理器标识
   * @param dataVersion 数据契约正整数版本，缺省构造为 1
   * @param outcomes 业务认可的结果事件编码；审批不能省略
   */
  public record Capability(
      Service service, Completion completion, String reference, String purpose,
      List<Documents.Field> fields, Set<String> outcomes, String dataContract, int dataVersion) {
    /** 兼容已有代码声明；打印使用带版本编码，审批按业务能力建立独立数据身份。 */
    public Capability(Service service, Completion completion, String reference, String purpose, List<Documents.Field> fields, Set<String> outcomes) {
      this(service,completion,reference,purpose,fields,outcomes,service==Service.PRINT?reference:reference+".data",1);
    }
    /** 数据结构从同一字段声明生成，不维护第二份字段清单；必填性取自字段声明且无隐式默认值。 */
    public com.appnexus.contracts.data.DataContract schema(){
      return new com.appnexus.contracts.data.DataContract(dataContract,dataVersion,service.name(),fields.stream().map(f->new com.appnexus.contracts.data.DataContract.Field(f.key(),com.appnexus.contracts.data.DataContract.Type.valueOf(f.type()),f.required(),f.unit())).toList());
    }
    /** 检查当前执行协议实际支持的完成方式，不允许配置声称支持尚未实现的行为。 */
    public Capability {
      if(dataContract==null||dataContract.isBlank()||dataVersion<1)throw new IllegalArgumentException("DATA_CONTRACT_INVALID");
      Objects.requireNonNull(service);
      Objects.requireNonNull(completion);
      if (reference == null || reference.isBlank()) {
        throw new IllegalArgumentException("INTERACTION_REFERENCE_REQUIRED");
      }
      if ((service == Service.BUSINESS && completion != Completion.SYNCHRONOUS)
          || (service == Service.WORKFLOW && completion != Completion.CALLBACK)
          || (service == Service.PRINT && completion != Completion.CONFIRMATION)) {
        throw new IllegalArgumentException("INTERACTION_COMPLETION_UNSUPPORTED");
      }
      fields = List.copyOf(fields);
      outcomes = Set.copyOf(outcomes);
      var keys = new HashSet<String>();
      for (var field : fields) {
        if (field.key() == null || field.key().isBlank() || !keys.add(field.key())
            || !Set.of("TEXT", "NUMBER", "BOOLEAN").contains(field.type())) {
          throw new IllegalArgumentException("INTERACTION_FIELD_INVALID");
        }
      }
      if (service == Service.WORKFLOW
          && (purpose == null || purpose.isBlank() || outcomes.isEmpty())) {
        throw new IllegalArgumentException("INTERACTION_WORKFLOW_PROTOCOL_REQUIRED");
      }
    }
  }
}
