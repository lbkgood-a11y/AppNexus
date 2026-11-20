package com.appnexus.workflow.adapter.flowable;

import com.appnexus.contracts.businessobject.BusinessObjects;
import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.application.ObjectApprovalService;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 公共对象审批用例的事务装饰器，不包含授权、业务规则或跨模块SQL。 */
public final class ObjectWorkflowAdapter implements WorkflowBusiness,BusinessObjects.Provider {
  private final ObjectApprovalService delegate;
  private final TransactionTemplate transaction;
  /**
   * @param provider 唯一对象声明及运行端口
   * @param operation 同一声明中的审批操作
   * @param platform 可信身份与统一功能准入桥接
   * @param tasks 延迟有效任务端口，防止引擎装配环
   * @param manager 与对象存储相同数据源的REQUIRED事务管理器
   */
  public ObjectWorkflowAdapter(BusinessObjects.Provider provider,BusinessObjects.Operation operation,
      WorkflowPlatform platform,Supplier<WorkflowTaskAccess> tasks,PlatformTransactionManager manager){
    delegate=new ObjectApprovalService(provider,operation,platform,tasks);
    transaction=new TransactionTemplate(manager);
  }
  @Override public BusinessObjects.Descriptor descriptor(){return delegate.descriptor();}
  @Override public Capability capability(){return delegate.capability();}
  @Override public Map<String,String> snapshot(Invocation invocation){return transaction.execute(status->delegate.snapshot(invocation));}
  @Override public Result execute(Invocation invocation){return transaction.execute(status->delegate.execute(invocation));}
}
