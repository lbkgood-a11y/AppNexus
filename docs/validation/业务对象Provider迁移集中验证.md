# 业务对象 Provider 迁移集中验证

本批集中迁入剩余 32 个对象。原路由、服务端鉴权和事务仍由真实用例执行。当前工作区已包含前 8 个新 Provider。

| 范围 | 新增唯一声明 |
| --- | --- |
| 平台、配置及审计 | `TenantInvitation`、`TenantParameter`、`NavigationMenu`、`BusinessObject`、`AuditEvent` |
| 权限 | `PlatformRole`、`TenantRole`、`AuthorizationTemplate`、`OrganizationAuthorization`、`PermissionCatalog` |
| 人员和组织 | `Person`、`OrganizationType`、`OrganizationRelation`、`Position`、`PositionAssignment` |
| 主数据 | `BusinessDictionary`、`Warehouse`、`ProductCategory`、`ProductAttribute`、`ProductTemplate`、`ProductUnit` |
| 流程 | `WorkflowDefinition`、`WorkflowInstance`、`WorkflowTask`、`WorkflowJob` |
| 打印 | `PrintDataContract`、`PrintDevice`、`PrintDocument`、`PrintJob`、`PrintOutput`、`PrintScene`、`PrintTemplate` |

## 变更对照

- D01–D03：剩余 32 项移除旧 Controller 类型声明，173 个已有操作复用原操作 ID、页面、入口和权限。无页面专用操作仍通过真实处理方法进入注册表。具有多条现有路径的操作保留别名路径。流程对象随工作流引擎装配。
- D01–D04：`AccountProfile`、`AccountMfa`、`AccountSession`、`AccountRoleAssignment` 并入 `Account`，`MemberRoleAssignment`、`MemberAuthorization` 并入 `TenantMember`，`OrganizationLeader` 并入 `OrganizationUnit`，`DictionaryItem` 并入 `BusinessDictionary`，`RoleAuthorization` 按管理域并入 `PlatformRole`、`TenantRole`。同步按钮和 API 展示能力绑定，共迁移 36 个操作归属；原 HTTP 地址和权限保留。已有目标 Provider 的声明版本递增。已检索 SQL 迁移中的旧 ID 引用，未找到需要随本批更新的迁移脚本；已部署数据库中可编辑配置的历史引用仍需在部署前复核。
- D02–D04：另补 43 个原无注解的专用端点，包括合作方字典、审计、目录、地图、租户上下文、打印代理、流程候选、请假查询和凭证恢复。账号本人、真实对象资格及凭证证明继续由原执行路径检查。通用执行动态按目标操作登记；CSRF 引导属于协议入口；旧打印直接提交继续固定拒绝。
- D05–D06：新增 32 项迁移的是专用接口契约。原业务安全与固定 SQL 仍执行，逐对象的统一数据范围、字段规则、跨租户和失败分支证据未齐。不得把声明注册、目录数量或编译通过视为这些检查通过。
- D07–D10：新增专用操作返回空公共能力，避免在无通用运行端口时开放通用命令、工作流、打印或消息绑定。已有工作流业务、打印运行和平台事件消费继续通过原执行链；工作流调度、引擎事件及业务终态的完整操作级接入仍需复核。已有请假、商品审批兼容操作仍需从旧独立注解归入唯一声明。
- D11–D12：未改 SQL、表结构、请求或响应 DTO。权限消歧和数据规则的完整版本与迁移验收、所有对象真实允许/拒绝入口验证及部署验收未完成。

## 验证与门禁

在独立源码副本统一执行后端组合测试，运行启用工作流的 50 项 Provider 唯一装配断言、注册表、主数据、集成客户端和平台事件回归。前端执行受影响操作指令用例和管理端类型检查。

前端在 Node 24 下运行管理端 `typecheck`（退出码 0）和 `src/directives/operation/operation.test.ts`（12 项通过）。全量源代码语法树扫描未报错；剩余无操作注解的生产 MVC 方法为通用对象执行、两处 CSRF 引导、固定拒绝的旧打印提交，另有独立 PoC。

首次扩展回归验证 32 项，31 项通过，打印模板发布错误 1 项。正式发布路径先把草稿 JSON 投影为 Map，又将 Map 的字符串交回 JSON 解析。改为复用原授权读取后，对同一事务中的原值执行一次 JSON 校验。最终统一回归 32 项全部通过，无失败、错误或跳过，退出码 0。

命令为 `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dtest=ObjectExecutionAssemblyTest,OperationRegistryTest,MasterDataIntegrationTest,IntegrationClientTest,ReliableEventsTest,AllOperationAuthorizationTest,WorkflowSecurityTest,PrintingRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`。MySQL 8.4 隔离库迁移至 V132。启用工作流的组合应用装配 50 个唯一 Provider；平台与租户的拒绝路径扫描、工作流安全和打印运行测试均通过。语法树对比前后均有 357 个 MVC 映射声明，无路径增删，新增绑定 43 个。

G01–G09、C01–C03、C05、C07–C10 尚需按实际结果复核。C04、C06 无 SQL 或数据库结构变更。前端改动只有操作引用，浏览器中的真实布局、按钮权限行为与全局继承仍待验证。当前运行实例未部署本批。
