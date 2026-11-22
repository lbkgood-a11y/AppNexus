package com.appnexus.masterdata.bootstrap;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.masterdata.application.product.*;
import com.appnexus.contracts.businessobject.BusinessObjectExecution;
import com.appnexus.contracts.workflow.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
/** 商品审批纯用例装配；功能开关与原流程保持一致，公共执行依赖延迟解析以保留原无环初始化。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.workflow.engine.enabled",havingValue="true")
public class ProductApprovalConfiguration {
 /** @param manager 同数据源事务管理器 @param products 父聚合协作 @param reads 商品读取公开用例 @param platform 权威审批契约 @param execution 延迟公共执行器 @param tasks 延迟任务准入 @param repository 固定审批仓储 @param content 固定快照编解码 @return 真实审批公开用例 */
 @Bean public ProductApprovalService productApprovalManagement(PlatformTransactionManager manager,ProductAggregateAccess products,ProductManagement reads,WorkflowPlatform platform,ObjectProvider<BusinessObjectExecution> execution,ObjectProvider<WorkflowTaskAccess> tasks,ProductApprovalRepository repository,ProductContent content,com.appnexus.platform.authorization.AccessPlanning planning,com.appnexus.platform.authorization.RowAccessControl rows,com.appnexus.platform.authorization.ScopedWriteGate writes){return new ProductApprovalService(new com.appnexus.masterdata.adapter.transaction.SpringRecordTransaction(manager),products,reads,platform,execution::getObject,tasks::getObject,repository,content,planning,rows,writes);}
}
