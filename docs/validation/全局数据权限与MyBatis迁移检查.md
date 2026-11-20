# 全局数据权限与 MyBatis 迁移检查

日期：2026-11-20。基准：d48bd9d 及任务开始前已有的未提交对象运行抽象改动。

## 目标与当前状态

需求为全仓自有数据访问逐入口审查、修复统一权限遗漏，并迁移 JdbcTemplate 到固定 MyBatis Mapper。当前尚在实施，**未满足交付门禁**。不得将本记录、文件清单或首批编译结果解释为全局完成。

生产数据库访问初始清单见同目录 `全局数据权限与MyBatis迁移清单.json`。清单按实际生产源码遍历，不读取 target/target-agent；同时记录 Mapper、JdbcTemplate、直接连接、迁移及权限线索。文本命中只是定位依据，必须追踪 HTTP、回调、任务、公开用例、实际 SQL、结果投影和事务，不能由“未引用 AccessPlanning”直接判定入口无授权。

首轮全仓枚举：104 个生产数据库访问文件，其中 75 个引用 JdbcTemplate；尚未逐条完成调用链与越权验收。测试夹具与历史迁移单独记录，不混入生产业务迁移完成数。

## 已确认的问题及误判纠正

1. `DataAccessIntegration` 仅从 DataAccessCatalog 已登记资源验证 Executor；完全没有 dataAccess 声明的业务操作不在扫描范围，因此不能证明全局覆盖。
2. `OperationRegistry` 仍支持 Controller 独立操作登记；Product 等旧操作未统一到 Provider，和现行完整声明要求存在差距。需从真实路由反查，而非只遍历 Provider。
3. `ProductCatalogQueryService.require` 已验证商品读取功能权限；先前仅查看 JdbcProductCatalogReads 就认定“无功能权限”不准确。其行范围、字段保护及引用目录语义仍需完整核验。
4. ProductMainService 当前使用功能权限；JdbcProductList、JdbcProductRecords 只有租户 SQL 条件。尚不能将租户隔离当作同租户行授权，需结合唯一声明和数据归属设计修复并验证。
5. ScopedOrganizationSummary 在 SQL 聚合前执行行范围，但仍手写组织谓词，未复用公共 RowAccessControl。
6. MyBatis TenantSqlInterceptor 是 Mapper SQL/租户门禁；RowAccessControl 是显式行范围编译接口。二者都不能替代应用操作准入及字段保护，迁移 ORM 不能自动补齐授权。

## 逐入口调用链复核（持续补齐）

| 入口及对象 | 实际调用链及事实 | 当前问题或待验边界 | 状态 |
|---|---|---|---|
| BusinessPartnerController / BusinessPartner | MasterDataService→PartnerDataAccess.plan→AccessPlanning；谓词由公共RowAccessControl生成，目标组织和原记录分别校验；主从响应及写入字段限制由PartnerDataAccess执行 | 仍使用JDBC；本轮最终迁移后须重跑原真实越权测试 | 未验证 |
| ProductController / Product列表、详情、创建、修改 | ProductV1Service.require和ProductMainService.require逐次hasPermission；列表→JdbcProductList；父记录→JdbcProductRecords；每条SQL绑定可信tenant | Controller旧声明未归入唯一Provider；不能因为有功能权限和租户条件就声称具有行范围与字段保护 | 未验证 |
| ProductController目录及版本候选 | ProductCatalogQueryService.require→masterdata.product:read→JdbcProductCatalogReads | 已有功能准入；引用目录是否按商品消费资格或目录本身数据范围开放，须明确声明并真实验证 | 未验证 |
| PersonController / Person列表及全量详情 | PersonDataService.require/PersonnelService.require→功能授权；PersonDataService返回phone/email等摘要，PersonnelService读取十类明细 | PersonDirectory自动字段绑定不是Person档案及明细的完整保护；需逐字段声明与真实写入/删除执行，不得假定同名“人员”目录已经覆盖档案 | 未验证 |
| OrganizationController / OrganizationUnit | MyBatisOrganizationUnitRepository→AccessPlanning→固定Mapper；ALL及组织集合进入各SQL | OrganizationDomainController→OrganizationStructure的同对象其他入口仍需独立追踪；不能用仓储执行器注册覆盖整个资源所有操作 | 未验证 |
| OrganizationSummaryController / OrganizationUnit统计 | ScopedOrganizationSummary→主体一致性校验→AccessPlanning→OrganizationSummaryMapper；分组前加租户及组织集合 | 已有真实聚合和主体错配反向证据；仍须与完整组织入口一起核验，不能替代其余写入检查 | 未验证 |
| ObjectExecutionController / 动态对象操作 | 公共BusinessObjectExecution读取唯一Provider运行端口；BusinessObjectPlatformBridge.authorizeRecord检查operationScopes、可信成员、记录组织及FieldProtection | 桥接只证明公共执行路径；不能自动覆盖同对象旧Controller中的其他SQL读写 | 未验证 |
| PartnerDictionaryController / 外部字典 | 可信机密客户端Token租户+SCOPE_dictionaries:read；固定联表双租户条件；摘要DTO；COUNT同条件 | 机器准入与统一对象操作声明仍需纳入矩阵；无普通成员上下文，不能伪造账号接入普通租户Mapper拦截器 | 未验证 |
| RecordAuditResponseAdvice / 审计附加投影 | 真实接口返回后，按RecordAuditSource固定表与已返回ID批量读取；可信租户独立绑定，每批200 | 组合层仍直接JDBC；需迁移到审计公开端口，不把构建注解动态表查询包装成任意SQL Mapper；审计不替代原接口数据权限 | 未验证 |
| 授权组织主体 / OrganizationMembership | MyBatisDataScopeAuthorization→组织公开端口→MyBatisOrganizationMembership→固定Mapper；成员、人员、归属、组织同租户且有效；UTC日期边界 | 已迁移Mapper；最终RULES组织主体、失效归属及跨租户实测待补验 | 未验证 |
| 字典业务引用 / BusinessValueCatalog | MyBatisBusinessValueCatalog→固定Mapper；引用需要实际事务；按字典编码加锁；保留历史值但拒绝新选停用或非法标准值 | MyBatis Map的布尔表达式类型与JDBC getBoolean不同，已改为显式状态列比较，须最终重编译验证 | 未验证 |

上轮《数据权限公共执行层抽象》明确“未接入第二个实际业务对象”，《数据权限内聚优化检查》明确“全部非单据业务对象覆盖仍未完成”。这些记录是局部证据，不是本轮全局任务的已完成前提；需要增加完整对象和实际入口执行覆盖，而非继续仅更新抽象或文档。

## 架构归属与已实施批次

| 用例及对象/操作 | 归属及公开入口 | 端口/适配器 | 表及事务 | 授权与边界 |
|---|---|---|---|---|
| 平台对象配置、发布、绑定解析 | business-object / BusinessObjectManagement | ConfigurationStore / MyBatisConfigurationStore + ConfigurationMapper | 原 business_object_* 配置表；原 REQUIRED 配置事务 | 原 BusinessObjectPlatform 管理及解析准入保持；Mapper 固定 SQL，参数绑定，精确表白名单 |

第一批将 JdbcConfigurationStore 的 27 条固定 SQL 迁入 ConfigurationMapper，保留 ConfigurationStore 业务端口、锁、CAS、审计、解析幂等及表结构；生产注入 Spring Mapper，隔离测试通过 SqlSessionTemplate 连接原数据源及事务管理器。没有提供任意 SQL 执行 Mapper，没有更改历史迁移或放宽既有断言。

后续已迁移编码存储16条SQL、授权失效Outbox写入、组织汇总、授权组织身份、字典引用和启动数据权限目录投影；主数据已迁移单位换算、商品父记录、完整性、快照、追加历史、列表及属性过滤、配置引用读取。新租户标准字典开通使用生产MyBatis预置器，历史标准资源不变，原无生产调用的JDBC预置方法已移除，测试夹具通过生产Mapper及同源事务预置。

当前清单屏蔽注释后仍有57个生产文件实际引用JdbcTemplate；RecordAuditDataSource的Javadoc提及不计为客户端使用，底层连接仍单列。尚未完成主数据其余写入/属性、人员、身份、安全、审批、打印、历史迁移及底层访问的全部迁移；不得称为完成。固定数据库时钟只允许 CodingMapper.now 的精确 SELECT CURRENT_TIMESTAMP(6)，不向其他 Mapper 开放任意无表查询。

迁移期间MyBatis默认SESSION本地缓存会掩盖同事务JDBC写入：商品完整性回归实际复现“启用商品清空必填属性未被拒绝”。统一配置改为STATEMENT、关闭二级缓存；同样配置用于隔离手工装配。Map读取保留NULL列，避免无意删除响应字段。调整后完整商品流程回归通过；数据库隔离级别不变，不以跨请求缓存掩盖授权故障。

SQL范围注解迁入 `building-blocks/mybatis-scope`：只依赖JDK的中立元数据，各业务适配器与平台拦截器共同依赖，未将授权、租户模型或SQL执行器移入building-blocks。新业务Mapper要求传入的可信租户与安全链租户一致，防止权限上下文与重绑定后的查询租户混用；旧原始参数覆盖测试契约保持。正式启动类显式扫描平台及主数据已接通Mapper，不能由启动包范围漏装。

组织汇总增加计划主体和可信请求账号/租户/成员一致性校验，拒绝以A租户的计划配合B租户的Mapper绑定；真实反向测试验证在调用AccessPlanning之前拒绝。列表的ALL/组织范围、跨租户同ID及计数保持原效果。

持续复查脚本 `node scripts/audit-data-access.mjs` 从生产源文件反查全部Controller，而非只读取已登记对象。当前清单还含59个Controller文件及266处独立操作声明源码线索；其完整MVC入口/授权矩阵仍未完成，不能将线索数直接说成已确认漏洞数。`node scripts/audit-data-access.mjs --strict` 在任一JDBC引用或独立操作声明仍存在时退出1，没有存量豁免名单；默认枚举成功不表示完成检查通过。源码文本扫描不能证明任何一条权限路径已通过。

## 声明同步复核 D01–D12

第一批及后续存储替换未增加对象/操作、API、页面、资源、数据范围、字段能力、专业能力、编码字段及运行协议。组织领域入口修复兑现原OrganizationUnit声明已承诺的范围，不扩大允许范围，因此保留版本与现有策略，不新增ALL迁移、不自动给旧角色授权；其他对象的声明缺口仍未关闭。

| 声明项 | 首批存储迁移及组织修复适用性 | 当前结论与证据 |
|---|---|---|
| D01 | 既有对象身份、归属、版本 | 静态复核无身份变化；中立模块不虚构业务对象 |
| D02 | 既有操作 | 无新增操作；组织list/create/update/move/inspectImpact沿原操作 |
| D03 | 既有页面、API及引用 | 路由和前端绑定不改；全局旧Controller迁入Provider仍未完成 |
| D04 | 管理域及准入 | 保留原功能授权；组织新增实际AccessPlanning执行，不用前端显隐放行 |
| D05 | 原已声明组织范围 | 原声明支持ALL/组织/子树；领域单位列表在SQL过滤，修改锁内复核，移动同时检查目标范围；8项真实组织回归通过 |
| D06 | 字段保护 | 不改变字段目录与脱敏算法；Map保留NULL列不是放行隐藏字段；全局Person档案及明细覆盖未完成 |
| D07 | 通用能力 | 保持原对象配置解析、编码、审批和打印协议，不登记新能力 |
| D08 | 数据契约事实 | 固定Mapper保留原列及JSON字段；商品快照仍由实际数据重读 |
| D09 | 编码 | 保留原编码声明、明确规则版本、同事务流水和回执；9项隔离编码回归通过 |
| D10 | 运行接入 | 保留原Provider运行端口，未重复接专业服务；工作区已有对象运行抽象改动继续保留 |
| D11 | 生命周期及事务 | 配置13项审批回归和商品完整流程通过；最终清理重建及新字典开通验证执行中 |
| D12 | 最终变更一致性及全局验收 | 未验证：全局真实入口、每对象字段/行权限和全部JDBC迁移未完成；严格检查仍失败 |

| 实际差异 | 对象/操作 | 声明处理 | 版本与迁移 | 证据 |
|---|---|---|---|---|
| ConfigurationStore JDBC 转固定 Mapper | 原对象配置、发布、解析操作 | 无业务契约变化，保留原准入；全局声明缺口另行修复 | 不改数据库结构或历史迁移；无需版本提升 | 27 条语句和端口方法一一对应；真实回归结果待记录 |
| 编码、字典引用/开通、启动目录投影及商品存储转Mapper | 原CodingEncoder/CodingRule、BusinessDictionary、Product/ProductUnit等既有操作 | 不新增功能或授权策略；全局旧操作声明仍需迁移 | 无表结构及历史迁移变化；中立范围注解仅技术包调整 | 固定语句、原事务；完整回归见日志，最终重建执行中 |
| OrganizationDomain单位列表、修改、移动、影响查询 | OrganizationUnit.list/create/update/move/inspectImpact | 兑现OrganizationUnitDeclaration原dataAccess声明，未扩大允许范围 | 保持声明版本，既有策略无需迁移；缺策略拒绝 | OrganizationSummaryTest新增真实同租户隐藏记录及跨租户同ID回归 |

## 逐项门禁

| 编号 | 适用性 | 状态 | 证据及未完成项 |
|---|---|---|---|
| G01 | 全局检查及数据库访问迁移 | 未验证 | 已建生产文件清单；完整入口矩阵未完成 |
| G02 | 持久化适配器及公共权限边界 | 未验证 | 已读规范；全局依赖回归待执行 |
| G03 | 统一对象/操作、行范围、字段保护 | 未验证 | 已识别目录扫描盲区；尚未全量修复 |
| G04 | 真实流程闭环 | 未验证 | 配置与审批 MySQL/Flowable 回归执行中 |
| G05 | 数据与安全 | 未验证 | 全量同租户越权、跨租户、无策略、撤权及字段验证未完成 |
| G06 | 编译回归 | 未验证 | 首批 persistence 及依赖编译成功；全仓最终编译与测试未完成 |
| G07 | 注释及契约 | 未验证 | 新 Mapper 中文职责说明；逐方法参数及最终行为复核未完成 |
| G08 | 证据与部署 | 未验证 | 未部署；全局日志及最终版本关联未完成 |
| G09 | 声明完整及同步 | 未验证 | 首批技术迁移无需声明变化；全局 D01–D12 未完成 |
| C01 | 对象及权限覆盖缺口 | 未验证 | Provider 与真实入口对照待完成 |
| C02 | 行范围执行 | 未验证 | 全局列表、COUNT、详情、写入和引用路径待逐项验证 |
| C03 | 字段限制 | 未验证 | 响应、修改、清空、集合及能力投影待回归 |
| C04 | 数据结构及审计 | 未验证 | 当前未改表；迁移后审计真实回归尚待执行 |
| C05 | 写入、锁及幂等 | 未验证 | 保留原事务结构；真实回滚及并发验证待完成 |
| C06 | 授权查询及性能 | 未验证 | 首批 SQL 形状保持；全局 EXPLAIN 待验证 |
| C07 | 前端 | 不适用 | 当前批次仅后端存储；后续如调整声明配置界面须重新判定 |
| C08 | 架构及包说明 | 未验证 | 原包新增 Mapper；架构与包说明测试待最终执行 |
| C09 | API/配置/依赖 | 未验证 | API 未变化；后续模块依赖及参数注释待核验 |
| C10 | 审批打印能力 | 未验证 | 配置解析被审批及打印使用，真实回归执行中 |

## 命令与证据

- `mvn -f backend/pom.xml -pl services/platform-service/adapters/persistence -am test -DskipTests -Dappnexus.build.directory=target-agent`：2026-11-20 17:43，BUILD SUCCESS，退出码0；只证明编译，未执行测试。日志 `.vite/mybatis-config-build.log`。
- `mvn -f backend/pom.xml -pl services/leave-poc -am test -Dtest=BusinessObjectIntegrationTest,LeavePocTest -Dsurefire.failIfNoSpecifiedTests=false -Pcontainer-tests -Dappnexus.build.directory=target-agent`：17:46完成，退出码0，真实隔离MySQL/Flowable，13项，0失败/错误/跳过。日志 `.vite/mybatis-config-integration.log`，报告在 leave-poc/target-agent/surefire-reports。
- `mvn -f backend/pom.xml -pl applications/monolith-app -am test -Dtest=OrganizationSummaryTest,CodingMySqlTest,TenantSqlIsolationTest,DataGrantCohesionTest,GlobalRecordAuditTest -Dsurefire.failIfNoSpecifiedTests=false -Pcontainer-tests -Dappnexus.build.directory=target-agent`：17:59完成，退出码0，23项，0失败/错误/跳过。包含真实全表审计、租户隔离、范围聚合、编码并发与幂等；不代表全局对象已接通。日志 `.vite/mybatis-permission-regression.log`。
- 初次组织汇总回归在编译旧实现后追加主体反向测试，出现旧实现空计划NPE；上述最终重编译回归已通过，不沿用失败产物。
- 字典引用、字段保护、授权EXPLAIN及架构最终回归执行中，日志 `.vite/mybatis-final-regression.log`。后续启动投影和字典状态投影修改尚需重编译补验。
- `.vite/mybatis-final-regression-v2.log`：18:10完成，退出0；业务12项及架构15项，0失败/错误/跳过。已覆盖字典引用状态、授权查询EXPLAIN、字段保护和固定编码时钟。后续中立元数据及主数据迁移须重新验证。
- `.vite/organization-scope-regression.log`：18:14完成，退出0；8项组织真实数据库验证，0失败/错误/跳过。
- `.vite/mybatis-product-record-test-v2.log`：18:24完成，退出0；修复语句缓存后完整商品数据库流程通过。后续快照/历史/列表及目录继续迁移。
- `.vite/mybatis-product-snapshot-test.log`：18:27完成，退出0；商品快照及历史迁移后的完整真实数据库流程通过。
- `.vite/mybatis-product-list-regression-v2.log`：商品完整数据库1项、正式商品API2项、组织范围2项、租户隔离7项通过；架构执行期间新增目录存储改名导致源代码断言与已编译产物不一致，因此该次整体失败，不列为最终通过。
- 当前最终清理重建：`mvn -f backend/pom.xml -pl architecture-tests -am clean test -Dtest=ProductCatalogMySqlTest,ProductV1IntegrationTest,TenantSqlIsolationTest,OrganizationSummaryTest,OrganizationAuthorizationTest,OrganizationBootstrapTest,PlatformAdministrationTest,BusinessValueDictionaryTest,CodingMySqlTest,CodingCapabilityTest,DataGrantCohesionTest,DataGrantQueryPlanTest,GlobalRecordAuditTest,UnifiedFieldProtectionTest,BusinessObjectIntegrationTest,LeavePocTest,ArchitectureRulesTest,ServiceLayerBoundaryTest,PackageDocumentationTest -Dsurefire.failIfNoSpecifiedTests=false -Pcontainer-tests -Dappnexus.build.directory=target-agent`。运行目录D:/AppNexus，独立构建目录清理不重启运行服务，日志`.vite/mybatis-full-batch-regression.log`；结果待完成。

结论：未满足交付门禁。未提交、未推送、未部署。本记录随实际实施和最终验证更新。

## 商品归属组织补充（用户已确认）

用户明确选择新增归属组织并支持组织及下级范围，设计见 `docs/design/商品归属组织与统一数据权限.md`。已开始V132归属列迁移和ProductAccess固定Mapper/公共范围转换；ProductAccess尚未装配，避免把未接通的对象登记为真实执行器。商品Provider收敛、全部入口执行、API归属参数、前端选择及最终正反向验收仍未完成，现有商品操作仍不能宣称已接通组织数据权限。

商品组织执行器草稿及V132资源在18:55受影响模块编译通过，退出0，未执行该迁移真实数据库验收；日志`.vite/product-ownership-scaffold-build.log`。后续实现不得仅以该编译结果开放新归属能力。

第一批最终clean回归在18:51完成，退出0：主数据完整流程1项、请假及配置13项、组合业务49项、架构17项，合计80项，均无失败/错误/跳过。日志`.vite/mybatis-full-batch-regression.log`。该回归早于V132及商品组织执行器草稿，不作为其通过证据。
