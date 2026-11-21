# 业务对象 Provider 迁移第三批

范围为 `IntegrationClient`、`CountryRegion`、`Subdivision`、`StorageArea`、`StorageLocation` 五个对象。描述符版本均为 1。当前运行实例待全量通过后部署。

| 检查 | 集成客户端 | 地理档案及库区库位 |
| --- | --- | --- |
| D01–D03 | 原四项列表、新增、修改、密钥轮换迁入唯一声明。页面、路由和操作 ID 保持，Controller 只引用。 | 四个 Provider 分别声明列表、新增、修改、启停。地理标准和分类树、库区库位的详情与直属上级筛选均走原列表读取权。 |
| D04 | 原 `EndpointOperation`、平台角色、MFA 和 CSRF；写入由原用例复核权威管理身份。 | 原用例继续检查可信租户成员的独立读、增、改、启停权限。 |
| D05–D06 | 固定租户绑定。列表和更新使用原受限摘要，原密钥仅在创建或轮换成功响应出现，无通用字段投影。 | 租户条件沿原固定 SQL 读写路径执行；四类档案沿用原字段集合，不增加通用字段投影或可配置行范围。 |
| D07–D09 | 无公共命令、工作流、打印或发号；原 OAuth scope 与专用 DTO 保持。 | 无公共命令、工作流、打印、发号或外部事实能力；原编码与层级校验仍由专用用例执行。 |
| D10–D11 | 复用 `IntegrationClients` 的事务、版本、撤权和事件推送。 | 复用 `MasterDataService`、固定 Mapper、层级事务和审计；不调整数据库结构或查询 SQL。 |
| D12 | 测试认证后的目录、四个实际管理 API、越权、无密钥摘要、版本冲突及 OAuth 相关回归。 | 测试四组唯一目录项、权限码、页面、真实数据权限、跨租户与层级写入回归。 |

声明同步和迁移复核：原 `BusinessObjectType` 和独立操作元数据均移出 Controller；保留旧对象/操作身份、权限码、运行路径和已有策略，无历史迁移。执行器登记分别来自 `IntegrationClients`、`MasterRecordMapper`。G01–G09、C01–C03、C05、C09 按本批实际证据复核；C04、C06–C08 无结构、查询、前端、包和依赖变更。OAuth 事件仍沿既有 Worker 链路，纳入 C10 回归。

## 实际结果

- 单独执行 `IntegrationClientTest,ReliableEventsTest` 已通过。
- 首轮地理回归通过真实 MySQL 主数据测试、授权测试和应用启动，既有商品目录断言仍写旧的 25 项。实际 `ProductDeclaration` 已声明配置读取和配置版本列表，基础操作应为 27 项，启用审核时为 30 项。本批同步修正回归预期，保留原精确数量断言。
- 在独立源码副本执行 `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dtest=MasterDataIntegrationTest,MasterDataMySqlTest,IntegrationClientTest,ReliableEventsTest -Dsurefire.failIfNoSpecifiedTests=false test`，退出码 0。使用真实 MySQL 8.4、Valkey 和应用上下文。四份报告分别为 2、1、8、9 项，合计 20 项通过，无失败、错误和跳过。
- D01–D06、D10–D12 的隔离验证通过。D07–D09 的通用能力不适用。集成客户端已验证无权访问、越权不写入、摘要无密钥、版本冲突和正常轮换；四类租户档案已验证唯一装配、允许和拒绝路径、跨租户约束、层级、CAS 与审计。生产式运行实例尚未部署和核对。
