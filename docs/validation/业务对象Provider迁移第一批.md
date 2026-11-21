# 业务对象 Provider 迁移第一批

范围为平台任务和外部字典引用。目标为 40 个新 Provider、9 个旧编码迁移和相关操作补齐。本批 4 个 HTTP 操作及平台事件 Worker 校验已通过隔离运行测试，现有实例的部署核对待全量迁移后执行。

| 检查 | 适用性与证据 | 结果 |
| --- | --- | --- |
| D01 对象 | `PlatformTask` 与 `DictionaryItemReference` 各有一个 Spring Provider，均为首次声明，版本 1。已从有权限账号调用平台目录并验证两项均存在。 | 通过 |
| D02 操作 | 平台任务列表、人工重试，外部字典引用登记、查询，以及平台事件 Worker 领取与消费。 | 通过 |
| D03 入口 | 四个 Controller 方法通过 `reference=true` 引用对应操作。启动校验允许唯一 Provider 声明的无页面专用 API，并限制合作方 OAuth scope 写法只用于无页面的 `/v1/` 接口。隔离实例启动并执行了四个入口。 | 通过 |
| D04 功能准入 | 平台任务继续使用 `EndpointOperation` 和平台管理员资格复核。外部引用继续使用合作方安全链、已发布 OAuth scope 和可信客户端上下文。非平台账号、缺 scope 的机器请求均被拒绝。 | HTTP 通过 |
| D05 行范围 | 平台任务为平台级记录，未声明可配置租户行范围。外部引用由原用例以权威客户端和固定租户限制读取与写入。同租户跨客户端和跨租户拒绝均通过。启动已验证资源有实际执行器。 | HTTP 通过 |
| D06 字段 | 专用响应只返回任务摘要或已授权引用；权限令牌与租约凭据不进入这些 DTO。字段保护实测待补。 | 未验证 |
| D07 通用能力 | 专用 API 和进程内定时操作；无流程、打印或公共业务命令能力。 | 调度适用项通过 |
| D08 数据契约 | 原任务摘要、引用请求及响应 DTO 保持现有契约，Provider 未声明通用事实投影。 | 专用 DTO 沿用 |
| D09 编码 | 两种记录标识由原用例生成，外部引用的幂等键仍由专用 API 接收，无公共发号。 | 不适用 |
| D10 运行接入 | 实际执行沿用 `TaskManagement`、`PartnerReferences`；专用操作无公共运行端口。 | HTTP 通过 |
| D11 生命周期与事务 | 已验证非法重试状态、未授权重试保持状态、引用请求重放和并发。任务恢复与审计继续共事务，本次未增加审计回滚的端到端用例。 | 部分验证 |
| D12 验收 | `ReliableEventsTest` 9 项、`IntegrationClientTest` 7 项均通过，无失败、错误或跳过。部署实例待总验收。 | 隔离验证通过 |

声明同步对照：`TaskManagementController` 的两个原入口接入 `PlatformTaskDeclaration`，`PartnerReferenceController` 的两个原入口接入 `DictionaryItemReferenceDeclaration`。两者均新增此前不存在的操作标识，HTTP 契约和既有权限语义保持。首次声明无需迁移历史快照，部署实例的受保护目录需另行复核。

## 交付门禁

| 门禁 | 适用性与证据 | 状态 |
| --- | --- | --- |
| G01、G02 | 按现有租户、配置、HTTP 适配与持久化职责落位，复用原公开用例、统一功能准入及代码目录。无新模块或 Java 包。整批仍包含后台系统入口。 | 未验证 |
| G03、G04、G05、C01、C02、C03、C05 | 真实 MySQL 和 Valkey 测试验证任务列表、越权、状态冲突、引用登记与读取、缺 scope、同租户跨客户端、跨租户及重放。适用的字段内容与审计回滚尚需专项证据。 | 部分验证 |
| G06 | `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dtest=IntegrationClientTest,ReliableEventsTest -Dsurefire.failIfNoSpecifiedTests=false test`。最终退出码 0，隔离 MySQL 8.4 与 Valkey，迁移至 V132。两份 Surefire 报告位于 `backend/applications/monolith-app/target/surefire-reports/`，合计 15 项通过。 | 通过 |
| G07、C09 | 新公开声明及执行器使用中文 Javadoc；原路由、授权码、响应和错误契约保留。 | 未验证 |
| G08、G09、C10 | 本文件记录 D01–D12，含 HTTP 和后台系统操作的隔离验证。当前改动未部署到本机运行实例。 | 部署待验 |
| C04、C06、C07、C08 | 未改 MySQL 结构、审计 SQL、查询条件、前端、Java 包名或构建依赖。本次复用原执行路径。 | 不适用 |

## 平台事件 Worker 补充复核

| 项目 | 变更与证据 | 状态 |
| --- | --- | --- |
| D01–D04、D07 | `PlatformTask` 描述符升级至 2，新增 `PlatformTask.processEvents`。固定 `@Scheduled` 方法在领取前使用统一 Provider 集合校验操作唯一性、SCHEDULE 触发和系统准入标识。该标识只用于进程内入口，无角色授权、页面、HTTP 或公共命令绑定。每轮最多 20 条，启动开关和原延迟配置保持既有行为。 | 通过 |
| D05–D06、D08–D09 | 租户及租约使用数据库权威记录，消费继续验证事件租户、租约有效期及唯一消费键。无客户端字段、数据投影或发号。 | 适用项通过 |
| D10–D12 | 新测试使用注入的真实声明集合和 MySQL，调用实际定时方法。缺声明在领取前拒绝，合法声明推进至 DONE，重复运行只留一份消费记录。错租户、旧租约、失败重试和并发领取回归通过。测试关闭后台自动轮询，直接调用定时方法。 | 通过 |
| 声明同步、迁移 | 新操作由 `PlatformTaskDeclaration` 唯一声明，`AuthorityEventWorker` 引用固定系统标识；不新增表、权限行或迁移文件。无公开操作标识改名，无存量策略迁移。 | 已复核 |

Worker 补充回归沿用上方 Maven 命令，退出码 0，16 项通过。报告为 `com.appnexus.platform.ReliableEventsTest.txt` 和 `com.appnexus.platform.IntegrationClientTest.txt`。
