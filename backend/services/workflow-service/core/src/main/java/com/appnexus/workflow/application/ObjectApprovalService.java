package com.appnexus.workflow.application;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.*;
import java.util.*;
import java.util.function.Supplier;

/** 从唯一对象操作生成审批桥接，统一任务校验、版本/轮次约束及终态幂等；对象只负责自身记录存取。 */
public final class ObjectApprovalService implements WorkflowBusiness,Provider {
  private final Provider provider;
  private final BusinessObjects.Operation operation;
  private final InteractionContract.Capability contract;
  private final WorkflowPlatform platform;
  private final Supplier<WorkflowTaskAccess> tasks;

  /**
   * @param provider 唯一对象声明及实际运行端口
   * @param operation 同一声明中的工作流操作
   * @param platform 可信身份及统一功能授权桥接
   * @param tasks 延迟有效任务授权端口，防止引擎装配环
   */
  public ObjectApprovalService(Provider provider,BusinessObjects.Operation operation,WorkflowPlatform platform,Supplier<WorkflowTaskAccess> tasks){
    this.provider=Objects.requireNonNull(provider);this.operation=operation;this.platform=platform;this.tasks=tasks;
    this.contract=operation.interaction().capabilities().stream().filter(c->c.service()==InteractionContract.Service.WORKFLOW).findFirst().orElseThrow();
    if(provider.runtime()==null)throw new IllegalArgumentException("BUSINESS_OBJECT_RUNTIME_REQUIRED");
  }
  @Override public Descriptor descriptor(){return provider.descriptor();}
  @Override public Capability capability(){return new Capability(contract.reference(),operation.nameZh(),operation.permissionCode(),operation.pageOperation()==null?null:operation.pageOperation().pagePath(),Set.of(),contract.fields().stream().map(Field::key).collect(java.util.stream.Collectors.toUnmodifiableSet()));}

  private Context context(Invocation i){var c=i.context();return new Context(c.tenantId(),c.actorId(),c.requestId(),c.personId(),c.organizationId());}
  private BusinessObjectRuntime.ApprovalRecord checked(Invocation i,boolean lock){
    if(!descriptor().objectType().equals(i.objectType())||!platform.active(i.context().tenantId(),i.context().actorId()))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_DENIED");
    var record=provider.runtime().approvalRecord(context(i),i.objectId(),lock);
    if(record.version()!=i.objectVersion()||!Objects.equals(record.organizationId(),i.organizationId()))throw new IllegalArgumentException("BUSINESS_OBJECT_APPROVAL_BASIS_CHANGED");
    if(!"EXECUTE".equals(i.operation())&&!"SUBMITTED".equals(record.state()))throw new IllegalArgumentException("BUSINESS_OBJECT_APPROVAL_STATE_CONFLICT");
    return record;
  }
  private boolean permitted(Invocation i){
    if(i.taskId()==null||contract.fields().isEmpty())return false;
    return tasks.get().permits(i.context(),i.taskId(),i.objectType(),i.objectId(),i.objectVersion(),contract.reference(),contract.fields().getFirst().key(),false);
  }
  @Override public Map<String,String> snapshot(Invocation i){
    var record=checked(i,false);
    if(i.workflow()==null){if(!platform.allowed(i.context(),operation.permissionCode())||!Objects.equals(record.applicantId(),i.context().actorId()))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_DENIED");}
    else if(!permitted(i))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_TASK_DENIED");
    var declaration=descriptor().dataAccess();
    var values=declaration==null?record.values():platform.projectData(i.context(),declaration.resources().getFirst().resource(),record.values());
    return contract.schema().encode(values);
  }
  @Override public Result execute(Invocation i){
    var record=checked(i,true);var ref=i.workflow();
    if(!"EXECUTE".equals(i.operation())&&!i.values().isEmpty())throw new SecurityException("BUSINESS_OBJECT_APPROVAL_INPUT_DENIED");
    if("WITHDRAW".equals(i.operation())){
      if(ref==null||!Objects.equals(ref.instanceId(),record.instanceId())||!Objects.equals(record.applicantId(),i.context().actorId()))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_DENIED");
      return new Result(false,Map.of());
    }
    if(!"EXECUTE".equals(i.operation())){
      if(!Set.of("APPROVE","REJECT").contains(i.operation())||!permitted(i))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_TASK_DENIED");
      return new Result(false,Map.of());
    }
    if(ref==null||ref.result()==null||!"APPROVAL".equals(ref.mode())||!Objects.equals(record.instanceId(),ref.instanceId())
        ||!Objects.equals(record.applicantId(),i.context().actorId())||!contract.outcomes().contains(ref.result()))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_RESULT_DENIED");
    // 可靠交付的result是协议元数据，必须与已验证实例终态一致，不能作为可写业务字段。
    if(!i.values().isEmpty()&&!i.values().equals(Map.of("result",ref.result())))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_INPUT_DENIED");
    if(!"SUBMITTED".equals(record.state())){
      if(!Objects.equals(record.deliveryId(),i.invocationId())||!Objects.equals(record.state(),ref.result()))throw new SecurityException("BUSINESS_OBJECT_APPROVAL_RESULT_CONFLICT");
      return new Result(false,Map.of());
    }
    provider.runtime().completeApproval(context(i),i.objectId(),i.objectVersion(),i.invocationId(),ref.instanceId(),ref.result());
    return new Result(false,Map.of());
  }
}
