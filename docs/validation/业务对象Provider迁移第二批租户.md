# 业务对象 Provider 迁移第二批——平台租户

| 检查 | 证据与变更 | 状态 |
| --- | --- | --- |
| D01–D03 | 原 `Tenant` 类型迁入唯一 `TenantDeclaration`，首次 Provider 版本 1。列表、创建、更新、启停均保持原操作 ID、页面和 HTTP 路径，四个入口改为引用声明。真实应用启动和认证目录检查通过。 | 通过 |
| D04–D06 | 平台管理域，原 `EndpointOperation` 保持权威功能准入，原用例逐次复核平台管理资格。普通账号读取与创建被拒绝。固定 DTO 为租户档案摘要，无租户业务记录或可配置行范围。执行登记来自真实 `MyBatisPlatformAdministration`。 | 适用项通过 |
| D07–D09 | 四个操作继续走专用接口。无通用命令、工作流、打印、发号或通用事实投影。 | 不适用 |
| D10–D11 | 复用原租户事务，已验证创建、地理数据初始化、幂等、修改和启停。审计失败回滚用例通过。 | 通过 |
| D12 | `PlatformAdministrationTest` 11 项通过，无失败、错误和跳过。覆盖认证后的对象目录、真实读写、普通账号拒绝、幂等和初始化事务。尚未部署到现有运行实例。 | 隔离验证通过 |
| 声明同步和迁移 | 旧 `BusinessObjectType` 由 Provider 取代，现有四个操作 ID 和权限码不变。无表、权限数据或页面配置迁移。 | 已复核 |

门禁 G01–G09、C01–C03、C05、C09 按上述用例和回归复核。无数据库结构与 SQL 变更、新包、新依赖或前端代码变更，C04、C06–C08 不适用。租户创建后原初始化事件执行证据纳入 G04；未扩展事件能力配置，纳入 D10 复核。现有运行实例在全部批次通过后统一部署。

执行命令为 `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dtest=PlatformAdministrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，退出码 0。运行环境为隔离 MySQL 8.4、Valkey 和完整 Spring 应用，Surefire 报告位于 `backend/applications/monolith-app/target/surefire-reports/com.appnexus.platform.PlatformAdministrationTest.txt`。
