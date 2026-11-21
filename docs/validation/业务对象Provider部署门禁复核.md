# 业务对象 Provider 部署门禁复核

本记录保留本轮部署前巡检结果。后续的 32 个资源声明及验证进展见[业务对象 DataAccess 补齐](业务对象DataAccess补齐.md)，发布判定仍需复核全部适用项。

| 检查 | 证据和结果 |
| --- | --- |
| 声明同步 D01–D03 | 请假 5 项以及商品审核 3 项已从 Controller 独立操作定义迁入原 `LeaveRequest`、`Product` Provider。引用 ID、页面和 API 保持。请假对象描述符从 2 升至 3，商品对象从 3 升至 4。审批驱动、能力身份以及既有商品范围授权维持。商品审核查询随现有引擎开关装配。 |
| D04–D06、G03、G05 | 对集中迁移的 32 个对象逐文件核对，32 个描述符目前均缺少 `DataAccess`。名单见[集中验证](业务对象Provider迁移集中验证.md)。现有 HTTP 服务鉴权不能证明统一数据范围、字段和必要执行器已经接通。适用性、真实正反向及失败路径仍缺逐对象证据，标记未验证。 |
| D07–D11、G04、G09 | 八个旧操作继续走原专用用例。工作流引擎条件接入正反向由组合测试覆盖，专用对象与后台链路的完整操作与数据边界默认仍待逐项核对。无路由或 SQL 迁移。 |
| G06、C08 | 隔离源码工作区运行 37 项组合回归及 17 项架构和包说明检查，两次命令均退出 0，无失败、错误或跳过。包括 50 个 Provider 唯一装配、全表审计、工作流、打印和越权拒绝。 |
| C07 | Node 24 下管理端生产构建退出码 0。新版本尚未部署，实际浏览器布局、全局间距继承、角色按钮仍未验收。 |
| C04、C06 | 这 8 个操作的声明迁移未改数据库结构与查询 SQL。全表审计仍作为本次总回归一并执行。 |
| G08、D12 | 部署条件尚未满足。现有进程及数据库未切换，发布后的认证目录数量未核对。 |

源码以 javac 语法树检查 357 个方法级 MVC 映射声明，未发现独立保留对象元数据的旧操作注解，语法诊断为空。后端命令为 `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dtest=ObjectExecutionAssemblyTest,OperationRegistryTest,MasterDataIntegrationTest,IntegrationClientTest,ReliableEventsTest,AllOperationAuthorizationTest,WorkflowSecurityTest,PrintingRuntimeTest,GlobalRecordAuditTest -Dsurefire.failIfNoSpecifiedTests=false test`。前端命令为 `pnpm --filter @appnexus/admin build`。

架构命令为 `mvn -q -f backend/pom.xml -pl architecture-tests -am -Dtest=PackageDocumentationTest,ServiceLayerBoundaryTest,ArchitectureRulesTest -Dsurefire.failIfNoSpecifiedTests=false test`。三份报告分别为包说明 1 项、分层边界 11 项、架构规则 5 项。D04–D06、D12 和 C07 的未验证项影响发布，本次部署判定为未通过。
