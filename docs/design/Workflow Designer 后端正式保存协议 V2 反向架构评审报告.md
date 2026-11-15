# Workflow Designer 后端正式保存协议 V2 反向架构评审报告

日期：2026-11-15。最终结论：**B. 需要修订协议后再评审**。本轮仅新增本文，不修改V2、代码、迁移、Demo或测试，不执行真实数据库验证。

## 1. 摘要

V2第11节的两事务排他锁排序论证在严格前提下成立，但还不是完整可实现协议的证明。当前正式实现没有控制行/epoch，存在V1旁路，所以不能据此证明当前系统安全。协议层需要补齐：控制行首次创建算法；锁内当前读及条件终态更新；旧目标终结的精确CAS条件；控制请求幂等冲突规则；事务传播/提交回执边界；摘要规范和墓碑保留。

未构造出在“所有最终写事务对同一不可替换控制行持锁、当前读校验epoch并持锁至真实提交”的完整前提下，旧Session在新激活提交后仍提交的反例。但发现V2文字允许的实现歧义可破坏终态不可逆性或误终结新会话。不能因此批准正式后端改造或直接进入协议验收测试。

## 2. 实际核查与协议差异

完整阅读V2、前置审计；读取Demo session-manager.ts、mock-document-server.ts理解Mock前提。生产源码证据以当前工作区为准：

| 证据 | 实际能力/边界 |
|---|---|
| WorkflowController.java 88–99、200–235 | V1 PUT save、POST publish；请求体无epoch；上下文与幂等头；无V2控制API |
| WorkflowRuntime.java 263–293 | save直接JdbcTemplate插入/更新wf_definition，revision条件更新，没有控制行锁 |
| 同文件297–349 | publish锁wf_definition并部署引擎/插wf_version；没有编辑Session校验 |
| 同文件353–368 | disable条件更新enabled/revision；无epoch |
| 同文件1300–1331 | mutate先插锁wf_request，再执行work；与V2定义→Session→Request相反 |
| 同文件56–64；WorkflowEngineConfiguration.java 29–53、75–87 | DataSource及PlatformTransactionManager注入；JdbcTemplate(source)、TransactionTemplate(manager)；Flowable同样接收二者，运行时绑定仍需验证 |
| platform-service/application/src/main/resources/application.properties 45–50 | URL和凭据来自环境；未明确事务隔离/主副本拓扑，不能从配置证明单主与锁语义 |
| V48__workflow_application.sql 3–19、47–51 | 定义/版本复合租户主键；wf_request tenant/actor/request主键；无控制/Session表 |
| WorkflowWorker.java 16–22；Runtime.dispatch/maintain 958、1094起 | 实例job交付维护；未检索到草稿异步写队列，不提供屏障 |
| TenantExecutionFilter.java 50–85；Runtime.require 1291–1299 | 可信租户/成员/功能授权存在；不是definition级epoch |

路径前缀WF=backend/services/workflow-service/adapters/flowable/src/main/java/com/appnexus/workflow/adapter/flowable，迁移位于同adapter/src/main/resources/db/migration；安全类位于backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security。

未发现生产配置中的RoutingDataSource或工作流显式隔离级别；不能以“搜索未发现”证明部署绝无读写分离。另一个授权服务显式设置READ_COMMITTED不能推导工作流相同。事务是否加入外层REQUIRED、是否在返回控制回执前真正提交，仍须真实集成验证。

## 3. Definition控制锁与首次创建

V2第3、6、9节要求控制行永不重置，但没有给出初始化竞争算法。仅SELECT不存在行FOR UPDATE不能作为可移植的单对象锁；不同隔离级别下缺失行/gap锁表现必须测试。

必须定义：先以tenant/definition唯一键执行原子insert-if-absent，再锁已存在行；冲突事务等待后读取持久epoch，不在冲突后使用本地epoch=0。插入冲突处理不得把deleted墓碑视为“不存在”，不得删除后重新插epoch0。创建、删除、恢复都由同一稳定控制行管理；新建定义的controlRequestId和对象身份预留也应幂等。

竞争A：W先锁，N等待，W成功/回滚后N才激活，排序成立。
竞争B：N先锁提交新epoch，W当前读失败，成立。
竞争C：T1受理/T2领取完成但W未锁，N终结请求；W必须重读终态，不能使用先前PROCESSING缓存。
竞争D：两个激活同observedEpoch，只有先提交者成功；后者当前读发现冲突。若重复键相同参数应返回同回执，不新建第二Session。
竞争E：两个实例初始化控制行，必须唯一插入收敛后锁同一行。当前未实现，V2未规定异常重试细节，NEEDS REVISION。

## 4. 激活与终结审查

observedEpoch防旧激活仅在主库锁内当前读比较且控制回执幂等时成立；客户端自动刷新epoch后重试会破坏意图保护，V2禁止此行为可保留。

关键反例：A已被B接管，epoch8/active=B。延迟的terminate(A,7)到达，若无条件epoch++并清active，再将A关闭，则B被误伤。V2第7节仅说“不能关闭后来新Session”，未指定更新条件。必须规定：只有active_session_id=A且write_epoch=7才能递增/清空；否则仅验证A已失效并返回针对A的屏障回执，绝不修改全局epoch或B。若target sid/epoch与归属不符拒绝。

控制键虽然数据模型有摘要字段，但API及执行规则未明确同键不同observedEpoch/takeover/target/owner如何冲突；不能仅复用旧回执。须与保存请求相同不可变信封规则。激活成功回包丢失查询旧回执，若会话又被接管，应返回历史成功+当前失效，不能重新激活。

屏障和激活同事务可排除中间写窗口，单独终结后再激活则必须重新CAS当前epoch，不能假定期间无人接管。

## 5. 请求状态机和恢复器

T2提前提交PROCESSING意味着worker尚未进入T3时恢复器也可获得定义锁。将其FAILED虽不一定是业务故障，但若T3强制重新检查状态，则可以安全取消；这是保守终结，不是证明worker已死亡。需定义恢复器选择/宽限仅影响可用性，不影响资格。

最危险反例：恢复器事务在RR快照中先看见PROCESSING；worker T3随后提交文档+SUCCEEDED；恢复器取得Definition锁后用普通一致性SELECT仍读旧PROCESSING，再无条件写FAILED。最终正文已提交但请求FAILED。取得父行锁不自动刷新既有快照。

V2第7节提请求锁，第9节T3写“重新检查”，恢复事务没有明确全部使用SELECT FOR UPDATE当前读，也没有终态更新CAS；因此命题B作为无附加条件语句不成立。必须规定恢复器定义→Session→Request锁定当前读，UPDATE ... WHERE status IN(CREATED,PROCESSING)，检查影响行数；SUCCEEDED/FAILED绝不覆盖。T3同样不能使用T2缓存状态或独立事务外读。

连接中断不等于数据库已回滚。恢复器只能等待同一控制锁：原提交已成功则读到SUCCEEDED；原事务仍活跃则等待/超时；原回滚才可终结。超时不授予判FAILED资格。恢复器崩溃、CREATED/PROCESSING长期滞留需要扫描调度和容量策略，不能用强行清记录解决。

## 6. 幂等与规范化审查

tenant+definition+requestId唯一及绑定actor/session/epoch/expectedRevision是合理基础。不同actor的查询与重复提交必须先授权，不得泄露旧结果。成功复用不能再次写正文。

V2第5节将数组/集合、数字及Map规范化留待后续，尚不足以保证实现互通。必须固化版本化canonical规则：对象键顺序、重复JSON键拒绝、缺省与null、数字精度/指数/-0、字符串Unicode处理、数组顺序、未知字段、schema版本；不能跨请求把有序数组排序。摘要之外还要保留不可变规范信封或可证明的等价比较策略；数据库payload禁止后改。

算法升级按记录canonicalizationVersion重算比较，不用当前算法误判历史请求；不能因清理payload而失去校验能力。墓碑清理若同时允许控制行/Session信息删除，旧键重建可二次执行，破坏命题C。必须保留拒绝依据或明确服务器强制有效期，拒绝未知老请求，不靠客户端时间。

## 7. 死锁与事务边界

具体死锁候选：V1 mutate锁请求R→等待定义D；V2 T3锁D→等待同一遗留请求R。V2若完全独立请求表，直接同R边不一定存在，但复用旧mutate/迁移桥接就有该反例。混用代码不可仅靠V2表设计免责。

批量终结：激活先锁Session A再B，另一管理操作先B再A；若控制行不是始终最先锁同一行或跨定义操作D1/D2顺序相反，可互相等待。跨定义批量操作需全局按tenant/definition排序，或每事务仅处理一个定义。外键检查、唯一索引冲突、审计/Flowable锁可能引入隐式边，需真实锁图验证。

T3若加入外层事务，方法返回不等于已提交；内层REQUIRES_NEW若只保护校验，锁提前释放后外层写也破坏排序。必须精确规定：一个完整定义写事务，不允许在持锁边界外分割，控制回执只在真实提交后返回。

死锁/lock timeout必须整个事务回滚，不能捕获SQL异常继续提交前面字段。仅在已明确回滚且无外部副作用时可原键重试；commit结果不明先查询，不能换键。批量请求数量大时不得分批释放定义锁后仍声称屏障原子完成；可先原子封闭epoch再异步逐条结算，但那是需另审的协议变化。

## 8. 所有Definition写路径清单

| 入口 | 当前位置 | 修改Definition | 控制锁 | 旁路 | 建议 |
|---|---|---|---|---|---|
| V1保存/新建 | Controller88–92→Runtime263–293 | 是 | 无 | 是 | 关闭或完整迁移，不自动隐式Session |
| 发布/重新发布 | Controller96–99→Runtime297–349 | published/enabled/revision、版本 | 仅旧wf_definition锁 | 是 | 纳入同一控制行及epoch，评估引擎事务 |
| 停用 | Controller104–107→Runtime353–368 | enabled/revision | 无控制锁 | 是 | 同上 |
| 独立启用 | 检索未定位独立入口，发布启用 | 发布路径 | 无V2锁 | 发布旁路 | 不虚构独立实现 |
| 删除/恢复 | 当前生产检索未发现 | 未实现证据 | 无 | 未证实 | 新接口必须墓碑/epoch规则 |
| 内部服务/管理API | WorkflowDefinitionCatalog只查发布可用性；未找到额外生产草稿SQL | 未发现额外 | 无额外证据 | 未证实 | 限定检索不能排除外部脚本 |
| 定时/消费者 | WorkflowWorker16–22→maintain/dispatch | 实例job，不见定义写 | 不适用 | 未发现 | 新增时静态门禁 |
| 数据迁移 | V48建表、V69增加版本/实例契约快照 | schema | 非在线写协议 | 部署风险 | 迁移期间写停机/受控 |
| 测试修复SQL | BusinessObjectIntegrationTest175–177 | enabled | 无 | 非生产 | 不作为生产旁路结论 |
| V2接口 | 未实现 | 尚无 | 尚无 | 不适用 | 设计不可作实现证据 |

因此当前正式系统不满足第11节全路径纳管前提，当前安全性证明不适用于部署。禁止以“后续统一”宣布已安全。

## 9. 安全命题A–E

| 命题 | 前提、危险时序与结论 |
|---|---|
| A 新激活后旧写不提交 | 需要同一不可替换主库行、锁内当前读epoch、所有写纳管并持锁至提交。W先/N先两种排序排除反例；当前V1无epoch，A10请求可在B读10后提交11，当前不成立；V2条件性成立 |
| B 获定义锁即可判PROCESSING失败 | 还需请求当前读、终态CAS、worker锁内重新检查、同主库；RR旧快照反例说明原命题过强，必须修订；当前无恢复器 |
| C 同键最多一次提交 | 唯一约束、不可变信封、原子成功回执、墓碑保留、无旁路；清理记录并重新受理旧键可能破坏。V2条件性成立，保留策略未定；当前成功幂等仅tenant/actor范围 |
| D 屏障后未到达旧请求也无资格 | epoch永久失效、受理和最终写均校验、控制墓碑不可重置；删控制行同ID重建epoch可复活旧token，V2原则禁止但算法未定；当前无屏障，不成立 |
| E 同锁建立完整串行化 | 仅对同定义且整个写事务持锁成立；同锁短检查后释放再写、不同主库、外部不可回滚副作用均反例。不能把同锁扩展成跨定义/引擎/外部系统全局串行化 |

已存在机制：revision条件UPDATE、wf_request唯一键/锁、TransactionTemplate、可信租户。控制行、Session epoch、T1/T2/T3与恢复器、屏障全部仅为设计要求。

## 10. 权限与HA

Session和epoch不能代替成员、对象与操作权限。activate/save/terminate/query分别复核；终结别人Session的权限需显式规定。权限在锁外校验后被撤销而请求仍提交，是独立授权时序问题；若要求撤权提交后无写，必须把授权版本校验纳入同步协议，定义它与Definition锁的顺序，不能写“再校验”就默认原子。

可信tenant来自Filter而非body。跨租户相同id/key以复合唯一键隔离；按请求键查询必须绑定tenant/definition及授权owner。账号切换的旧请求仍绑定原可信上下文，不能改写新租户；不能据此认为旧租户写已取消。

事务崩溃由DB回滚未提交写，不能证明丢失commit回包时未提交。成功回执后主库切换丢数据会让epoch和请求墓碑倒退，核心证明失效；必须依赖不丢已确认提交、单主和故障切换栅栏。状态读副本可读旧PROCESSING/UNKNOWN，不能作为恢复器终态决策。两个实例不同主库各锁自己的D可各自成功，数据库行锁本身不能排除双主。

## 11. 真实MySQL集成测试矩阵（设计，未运行）

共用准备：真实InnoDB、两应用实例独立连接池、同主库；初值revision10/epoch7/activeA、文档M0；每例隔离数据。以显式测试屏障控制到锁/提交位置，不能用sleep推测。每例验证文档、revision、epoch、Session及请求表，必要时performance_schema锁信息；下列“预期”不是实测。

| # | 精确调度/故障点 | 预期最终状态与破坏的不变量 |
|---|---|---|
| 1 | W锁D暂停，N激活等待；放行W提交，再N | rev11/epoch8，A成功在N前；若N先回有效后A提交破坏A |
| 2 | 两实例同observed7激活，同时争锁 | 仅一Session有效/epoch8；另一个CAS冲突，验证单资格 |
| 3 | W持锁，terminate等待；W提交后终结 | 回执保留成功，A关闭，无屏障后旧写 |
| 4 | 屏障提交后才发送旧请求包 | FAILED/FENCED，正文与rev不变，验证D |
| 5 | T2 PROCESSING后停worker；恢复器锁D判失败；再放worker | FAILED且rev10，worker不得重启执行，验证B |
| 6 | T3写文档未commit时杀应用/断DB连接 | 回滚M0/rev10；恢复器锁定确认FAILED，验证原子性 |
| 7 | DBcommit完成后丢HTTP/commit回包 | SUCCEEDED/rev11，同键查询复用，不写FAILED |
| 8 | 恢复器先建旧快照，W提交，恢复器再锁D | 当前读见SUCCEEDED，不覆盖FAILED；专测RR反例 |
| 9 | 同key不同payload双实例受理 | 一信封保留，另409，无二次revision |
| 10 | 同key改变epoch/session/revision | IDEMPOTENCY_CONFLICT，不重绑owner |
| 11 | 空控制表两实例同ID预留 | 一控制行，后续epoch单调，无两套资格 |
| 12 | 删除保留墓碑后恢复同ID，重放旧token | 拒绝旧token，epoch不重置，验证D |
| 13 | V2激活后调用V1 save | 必须禁用/拒绝；当前实现预计不满足，证实旁路 |
| 14 | 后台/内部SQL写路径绕过控制锁 | 门禁检查失败，不允许测试“通过”；验证全路径前提 |
| 15 | 延迟副本同步后查询/恢复请求 | 状态确认强制主库，不用旧副本判终态 |
| 16 | 激活后撤权，再保存/查询别人request | 拒绝且不泄露正文；锁外撤权竞争按批准语义验证 |
| 17 | 大量CREATED/PROCESSING终结，中途锁超时 | 屏障不报成功；无半资格开放，复查请求与epoch |
| 18 | 激活commit丢响应，同control key重试；改参数再试 | 只升一次epoch，改参409，验证控制幂等 |
| 19 | B已epoch8后延迟terminate(A,7) | B及epoch8不变，仅A屏障回执，验证不误伤 |
| 20 | 同一事务旧快照+不同传播/数据源配置 | 不一致装配必须启动/测试拒绝，不接受伪行锁 |
| 21 | 主库故障切换/双主模拟 | 不丢已确认事务或阻止服务；丢失则明确不满足保证 |

每例保存线程时序、SQL连接/事务标识、锁等待及最终表快照；锁超时与死锁回滚重试均使用原键，不能通过改键消除冲突。

## 12. 必须修订与可保留项

必须修订：控制行原子预留/墓碑算法；terminate目标CAS；所有关键Session/Request读使用主库锁定当前读，终态CAS不可覆盖；控制键完整冲突语义；规范化版本及保留；实际事务传播和提交边界；跨定义锁顺序/批量终结上限；授权撤销时序与HA前提。

可保留：Definition锁排序的核心思想、epoch由服务器生成、UNKNOWN仅客户端观察、同事务正文/revision/成功回执、query不等于屏障、旧会话不可复活、前端generation与服务器资格分离。

## 13. 最终结论与保护

结论B：需要修订协议后再评审。第11节是条件性正确的局部证明，当前不是覆盖全部实现分支的完整协议证明。当前正式代码不满足其前提。暂不批准正式后端改造，也不批准将现稿直接进入协议验收式MySQL验证；可以先完善文档和测试规格，再评审是否进入隔离集成实现。

静态无法确认：实际MySQL隔离/存储引擎、事务数据源绑定、主副本路由、外层事务、锁超时恢复、HA数据丢失界限。均列为待真实集成验证，不用Mock代替。

开始git status记录260项未提交条目，状态/既有文件哈希保存/tmp/v2-reverse-baseline.json。只新增本文，结束复核结果在交付回复记录；既有代码、文档、Demo及六个外部文件不写入，不执行reset/clean/checkout/restore。
