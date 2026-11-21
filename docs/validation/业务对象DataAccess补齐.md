# 业务对象 DataAccess 补齐

| 管理域 | 本批对象和资源边界 | 执行接入 |
| --- | --- | --- |
| 租户主数据 | `ProductCategory` 及分类配置，`ProductUnit` 及换算，`ProductAttribute` 及分组、版本和选项，`ProductTemplate` 及版本、条目 | 商品目录、模板、属性和换算的原 MyBatis 仓储继续以可信租户限制读写。 |
| 租户组织 | `OrganizationType`、`OrganizationRelation`、`Position`、`OrganizationAuthorization` | 原组织存储与授权实现继续使用验证后的租户，关联目标和权限受专用用例约束。 |
| 租户配置与角色 | `TenantParameter`、`BusinessDictionary` 及字典项、`TenantRole` 及角色授权 | 参数键白名单、字典主从、角色所有者和授权复核沿用原链路。平台代管租户角色继续执行平台准入，目标数据仍属于指定租户。 |
| 平台配置与角色 | `PlatformRole` 及角色授权、`TenantInvitation`、`NavigationMenu` | 原管理资格、目标身份和专用投影保持。邀请凭证仍经既有一次性证明校验，菜单的本人视图仍由原导航用例裁剪。 |
| 租户人员与仓库 | `Person` 及人员明细、`PositionAssignment`、`Warehouse` | 人员档案和明细的实际仓储、组织领域适配器和仓库固定映射提供执行资源。个人资料与仓库联系人等字段目前未声明可配置保护；逐字段响应及写入验收仍未完成。 |
| 工作流 | `WorkflowDefinition` 及版本、`WorkflowInstance` 及参与人、`WorkflowTask` 及指派、`WorkflowJob` | 流程存储映射提供执行资源，用户仍由原流程用例复核任务资格、发起人和可信租户。系统交付仍保留原作业和租约边界。 |
| 打印 | `PrintDataContract`、`PrintDevice` 及队列、`PrintDocument`、`PrintJob` 及产物和回执、`PrintOutput`、`PrintScene`、`PrintTemplate` 及版本 | 相应的打印模板、任务、流程、输出和回执固定 Mapper 提供执行资源；代理调用仍沿原机器资格、可信租户与设备检查。 |
| 平台和租户双域 | `BusinessObject`、`AuditEvent`、`AuthorizationTemplate`、`PermissionCatalog` | 同一个 Provider 内声明两个独立资源根，按数据的真实管理域分别投影。当前双域模型仅支持固定边界，不开放两域共用的可配置行范围。 |

声明同步复核：以上 32 个描述符增加实际使用的管理域和主从资源，版本从 1 升至 2。原对象码、操作 ID、功能授权、SQL、表结构与 HTTP 路由保留。数据权限协议新增可选的第二管理域，原单域构造入口与投影保持。打印、流程执行适配器通过公开契约参与启动校验，避免依赖平台权限实现。现有固定所有者、租户和业务约束仍由真实执行路径实施。系统未向角色界面开放尚未接通的可配置范围或字段策略。敏感邀请凭证的哈希不登记为可查看字段。目录版本令牌随资源新增变化，已有授权编辑窗口使用原并发检查，需刷新后再保存；无存量资源改名或策略数据迁移。

D01–D06 与 C01–C03 按各自入口、资源、执行端口复核。D07–D11 的现有业务和通用能力契约保持，未触发新的数据库、前端或 HTTP 契约迁移。D12 正反向集成验证进行中。商品独立回归 `ProductCatalogMySqlTest` 在子分类商品创建处抛出 `PRODUCT_TEMPLATE_NOT_PUBLISHED`，其夹具将所有模板槽置空；本批未改模板发布条件，未将该用例记为通过。早期装配拒绝未作为 Bean 注册的单位换算仓储。执行登记已移到原来就由 Spring 管理的固定换算 Mapper，启动校验保持。

完整装配验证和逐对象安全验收分别判定。全量 D12 正反向与字段保护仍需按对象补充，当前不标记部署门禁通过。

## 实际验证

- 启用工作流的 `ObjectExecutionAssemblyTest` 3 项通过，验证 50 个唯一 Provider、目标 32 个均有资源声明、4 个双域对象分别进入平台和租户目录。应用启动校验要求声明的每个资源都有执行器。
- `MasterDataIntegrationTest` 3 项及 `DataAccessDeclarationTest` 4 项通过，覆盖租户资源、主从关系、未接通的可配置行范围、目录去重和原字段保护。字典、菜单、组织错误协议、组织授权与打印运行的对应测试均通过。
- 扩展回归 60 项，53 项首次通过。另 7 项收到预期的业务拒绝，但测试仍断言旧 HTTP 异常类型。按现有领域契约校准断言后，授权模板、邀请、租户参数和审计的 9 项定向复跑全部通过，退出码 0。生产拒绝行为未修改。
- 扩展回归在隔离源码副本执行，命令为 `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dtest=MasterDataIntegrationTest,TenantParameterTest,DictionaryStandardIntegrationTest,RoleManagementTest,OrganizationGrantManagementTest,TenantInvitationTest,ObjectExecutionAssemblyTest,OrganizationErrorHttpTest,MenuManagementTest,DataAccessDeclarationTest,PrintingRuntimeTest,WorkflowSecurityTest,PolicyTemplateTest,UnifiedAuditTest -Dsurefire.failIfNoSpecifiedTests=false test`。定向使用同一命令结构，测试集为 `PolicyTemplateTest,TenantInvitationTest,TenantParameterTest,UnifiedAuditTest`。隔离 MySQL 8.4 迁移至 V132，运行记录来自 Surefire 报告。
