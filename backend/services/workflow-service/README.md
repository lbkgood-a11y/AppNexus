# 工作流服务

工作流与 platform-service 平级，初期同JVM组合启动；仅启动装配层引用实现，平台桥接只依赖 workflow-contracts。

## 当前实现

- core：受控节点/连接模型、权威图校验、无副作用样本路径预演，无框架依赖。
- adapters/flowable：Flowable 8.0.0 BPMN编译、部署、实例与人工任务执行、JDBC事务、HTTP、可靠交付与恢复。
- 审批型结束后独立交付结果；端到端型通过动作/等待节点完成业务后结束。
- 审核、办理、会签/或签/依次、条件分支/汇合、结构化并行、自动动作、异步回传、抄送、受业务校验的撤回、重新分配和重试。
- 不可变发布版本、乐观锁草稿、按可信业务快照自动匹配、活动实例唯一约束、请求幂等、审批依据复核及追加审计。
- frontend/modules/workflow：任务工作台、可视化表单设计器、样本预演、发布版本查看和运行恢复，复用平台UI。

没有注册生产WMS或考核替身。真实业务模块实现 WorkflowBusiness，负责单据状态、组织/版本、字段权限、强制限制、原文读取和操作幂等。
首批不支持循环、复杂子流程、任意前序节点退回、委托/加签/转交、通用补偿编辑器及在途迁移；这些不得通过任意BPMN/脚本绕过。

## 启用与迁移

| 配置 | 环境变量 | 默认值 | 用途与约束 |
| --- | --- | --- | --- |
| app.workflow.engine.enabled | APP_WORKFLOW_ENGINE_ENABLED | false | 装配引擎及API；须先执行V47—V49受控迁移，运行时不自动建表或升级 |
| app.workflow.worker.enabled | APP_WORKFLOW_WORKER_ENABLED | false | 引擎启用后才生效；注册幂等业务适配器后开启可靠交付扫描 |

Flyway使用组合应用已有迁移入口；V47为锁定版本上游common/engine/history脚本，V48归工作流所有，V49登记平台权限和默认停用菜单。
关闭引擎不阻止显式启用的Flyway执行迁移。迁移不依赖引擎启动；引擎等待数据库初始化完成。
已存在ACT_*人工建表环境须先进行版本、结构和基线审查，禁止直接删表或跳过迁移。

启用真实业务能力后，由菜单管理员开启工作流中心。有效成员可进入任务工作台；read/edit/publish/start/operate管理能力逐项授权，任务办理另验任务归属。
扫描周期1000毫秒，每轮最多20个交付；租约120秒，技术重试间隔30秒；节点重试0—5次，期限60—2592000秒。超时或业务拒绝转人工，不自动通过。
适配器网络超时必须小于租约，外部副作用按invocationId持久化幂等，不能承诺跨服务恰好一次。人工恢复要求原因，稳定调用ID不变，恢复期限为86400秒。

## 验证命令

在 backend 执行（容器测试需要Docker）：

```powershell
mvn -pl services/workflow-service/architecture-tests -am test "-Dtest=WorkflowRuntimeTest,WorkflowModelTest,WorkflowBoundaryTest,WorkflowEngineConfigurationTest,WorkflowSecurityTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest.excluded-groups="
```

接口、业务接入要求和当前验证限制见 docs/design/工作流服务构建交付.md。编译和测试不代替真实业务接入或浏览器视觉验收。
