# Workflow Designer 后端正式保存协议 V2.3 独立反向架构复审报告

日期：2026-11-15；结论：**B 修订后复审**。P0两项、P1四项、P2两项。仅静态分析、源码检索与三组摘要复算，不运行测试、构建、服务或数据库操作。文档规则不等于正式实现。

## 1. 证据分类与总体裁决

协议定义：统一W主键含operation、三类状态机、DISABLE独立契约、RESTORE disabled/published0、固定域和A路径准入均已明确，V2.2的相应歧义得到实质收敛。逻辑推导：严格同D锁/当前读/真实commit前提下，旧资格排序可成立。源码事实：当前V1没有这些实体。待实验：MySQL锁序、Flowable资源/缓存/副作用和commit恢复。外部批准：授权宽限、权限映射、永久正文保留及HA。

仍不能判A：幂等重放与资格检查先后不完整；Definition全量屏障与按Session SQL衔接不足；P4终态矛盾组合缺规则；RESTORE后发布版本分配未明确排除旧published算法。可继续细化实验规格，但不得进入完整协议隔离实现/验收或正式改造。

## 2. 问题清单

| 编号 | 章节/反例/为何未排除 | 影响及最小修订 | 阻断 |
|---|---|---|---|
| P0-01 | §4按sid/epoch扫描，§7 DELETE称终结全部旧W，缺Definition范围扫描/枚举历史Session规则。若遗留S1有PROCESSING、当前S2，DELETE只扫S2则S1仍非终态 | D的“全部终结”及64前提不完整；明确D范围查询全部非终态、S固定序逐点锁、异常遗留>64保护策略；区分资格封闭和账本结算 | 协议复审/完整实验 |
| P0-02 | §8 P4只明确SUCCEEDED缺映射、PROCESSING有残留等，未定义FAILED有deployment/映射及CREATED有部署；通用“终态复用”会返回失败而隐藏孤儿 | G/A路径准入不足；给完整矩阵并先查一致性再回终态，FAILED矛盾必须保护 | 发布实验准备完成前 |
| P1-01 | §3 T1先核权限/LIVE/epoch再W，与同键复用冲突：A成功、B接管后A PUT原key可能FENCED；GET读取权却可查成功 | C不必二次写但UNKNOWN恢复契约不确定；D锁后读取绑定历史S/W，先授权查询归属/比较信封，终态只回执；仅新执行检查当前资格 | 幂等互通复审 |
| P1-02 | §6只说分配业务version，§7 RESTORE published0；若沿当前Runtime version=published+1，历史v1存在时再次分配v1冲突 | G及恢复后重新发布可用性；独立永久next_version或锁内历史最大版本+1，溢出规则，禁止以published指针分配 | 发布/恢复实施 |
| P1-03 | §7控制schema缺参数类型/null/数值字符串细则，§10只有写请求黄金向量，无完整控制黄金字节；同控制key异操作向量只是文字 | 控制幂等互通；封闭每操作完整schema、绑定definition/path，补完整ACTIVATE/TERMINATE黄金 | 互通实验 |
| P1-04 | §3/4称ORDER BY FOR UPDATE确保固定序，SQL没有匹配session/epoch/status索引；InnoDB可扫描锁后filesort | 不一定同D内死锁，但不能由结果排序证明锁全序；索引/EXPLAIN或D锁后收集ID并按全主键逐条当前读锁，禁止先W再D | MySQL规格/锁序证明 |
| P2-01 | §12 disable候选workflow:publish及查询权、新delete/restore权限未获批准，授权缓存及时性未知 | 授权时序；权限目录/独立操作准入和审批，不能当已有权限事实 | 正式实施 |
| P2-02 | 永久canonical保存可能含个人信息/Prompt，未定义删除合规例外与负责人 | 幂等墓碑不能无审批等同永留敏感正文；最小不敏感信封/摘要方案需安全隐私评估 | 正式实施/数据保留批准 |

P0-01的历史非终态在完全正确的新协议中理论上应不存在，但首次迁移、旧缺陷或崩溃恢复不变量异常正是屏障需确定处理的输入；不得只称“唯一ACTIVE”就不定义检查。

## 3. 统一账本与重放时序

唯一键(tenant,definition,op,key)及跨op独立规则清楚；同UUID三op不复用。状态路径明确，终态永久。T2成功响应丢失不允许第二worker；原领取者没进入T3可被T4保守FAILED，迟到worker必须当前读停止。

重放反例：T3(A,SAVE,r)commit成功→T0接管B/epoch8→A重复PUT相同r；T1先按当前资格拒绝与历史回执复用存在两种实现。S锁顺序本身不冲突：可锁历史S后锁W，不要求历史S必须ACTIVE；但文档需明确此区别。GET只用读取权查询历史，不授写权；重复PUT终态也应按批准的查询权复用而不执行。权限仍撤销全部读取则403，不承诺泄露回执。

worker/恢复器：W先D→S→W，正文+SUCCEEDED commit，R后当前读成功不可覆盖；R先D将PROCESSING失败commit，迟到W读FAILED停止。W等待D时R可先胜，W持D时R只能等待/超时。commit未知不猜FAILED，原key查询；业务拒绝可以提交失败，SQL异常必须整事务回滚。

## 4. 跨类型锁序和屏障

SAVE/PUBLISH同rev10争D，先commit11后者冲突；SAVE/DISABLE、PUBLISH/DISABLE同理。PUBLISH/DELETE、PUBLISH/接管先D者完成后后者重验；DISABLE/RESTORE仅LIVE和DELETED各有前置，后获得D按当前生命周期拒绝或使用新rev。全部条件依赖P3真正同资源事务。

多个S终结必须D后按S UUID字节序，再W按(op,key)逐点；不同D首版禁止跨定义事务，本地线程启动顺序不作证明。ORDER BY返回序不能自动证明InnoDB获得索引/记录锁顺序，见P1-04；同D排他可阻止合规worker并发锁W，但不能代替明确算法或排除外键/管理旁路。

TERMINATE旧A7、D=B8时不得改D，只读A历史回执，已定义；迟到T1旧epoch不能建可执行记录，迟到T3不能写。DELETE需要按Definition而非仅active sid识别所有非终态；发现历史请求可先封资格但不得分批成功屏障，按现有协议必须同事务结算或失败/保护。RESTORE不得重新受理旧终态请求。新controlRequestId重复操作不是原键恢复：ACTIVATE observedEpoch挡旧观察，其他操作的targetRevision/epoch挡重复；客户端仍不得未知时换键。

64口径已包含三op及CREATED/PROCESSING；同键不占额，终态释放，第65D锁下拒绝。若旧S非终态残留，按current S计数无法推出Definition总≤64，需P0-01异常输入策略。恢复器不直接改计数，基于实际行状态统计，不能缓存计数后无锁受理。

## 5. DISABLE与运行时

协议DISABLE新key即使已disabled也rev+1，同键不再增；published保持、无引擎部署、实例继续，重新启用仅PUBLISH新不可变版本。与PUBLISH同revision竞争唯一胜者，后续操作者需新rev并明确意图；不自动覆盖。

源码Runtime.start在约442–445读取wf_definition d.enabled=TRUE并FOR SHARE，published目录约32–34也过滤enabled。能够确认现有入口不是完全忽略enabled；但当前没有D.lifecycle/deleted字段检查，不能声称V2删除/恢复过滤已实现。固定版本绑定可指向历史发布版本，RESTORE published0+disabled阻止启动，但重新发布后允许旧固定绑定是否预期需业务审批，不应把“历史版本不可变”与“仅最新可启动”混为一谈。

权限映射在协议标明候选且审批未完成，不能当作产品已认可。直接引擎RuntimeService启动是否被业务封闭需要运行准入审查，不以设计器禁用按钮代替。

## 6. Flowable A与P4完整裁决矩阵

当前源码WorkflowEngineConfiguration29–53、80–87同注入参数是配置事实。WorkflowRuntime.publish297–349 deploy后插业务版本与定义更新，mutate1300–1331为TransactionTemplate；没有真实连接参与、传播、引擎缓存、Job或外部监听故障证据。A尚未通过，B/C未批准，不能补偿替代原子性。

下表是复审建议的完整规格，不是V2.3已有完整定义，更不是执行结果。所有判断先获取D及对应S/W，等待原事务结束并访问权威主库；锁不可得则等待/超时保持UNKNOWN，不能按表强判。E代表唯一匹配部署且processDefinition一致，M代表正确唯一业务映射。

| W | E/M | 应有裁决 |
|---|---|---|
| CREATED | 无/无 | 可按策略T2领取或T4保守FAILED，不能再次并行worker |
| CREATED | 有/任意 | WRITE_PROTECTED+人工核查，受理阶段不应有部署 |
| PROCESSING | 无/无 | 原事务结束确认后CAS FAILED |
| PROCESSING | 有/无 | WRITE_PROTECTED，不删孤儿不重部署 |
| PROCESSING | 无/有 | WRITE_PROTECTED，映射指空 |
| PROCESSING | 有/有 | WRITE_PROTECTED，不把PROCESSING擅自升SUCCEEDED |
| SUCCEEDED | 完整一致 | 可返回历史成功，不再部署 |
| SUCCEEDED | 无/有 | WRITE_PROTECTED，成功缺部署 |
| SUCCEEDED | 有/无 | WRITE_PROTECTED，成功缺映射 |
| SUCCEEDED | 无/无 | WRITE_PROTECTED，成功凭据矛盾 |
| FAILED | 无/无 | 可返回历史失败，不再执行 |
| FAILED | 有/任意 | WRITE_PROTECTED+人工核查，不用终态复用掩盖孤儿 |
| FAILED | 任意/有 | WRITE_PROTECTED+人工核查，不改回成功 |
| 任意 | 多deployment同key | WRITE_PROTECTED、排除其他租户/名称碰撞后人工核查 |
| 任意 | deployment/processDefinition错配 | WRITE_PROTECTED，不重绑映射 |

P4持久源能发现DB残留，不能发现已发出的外部邮件/HTTP、其他实例提前缓存或已运行Job副作用。因此第8节额外观测仍是A准入不可省略前提；不能仅表记录干净就证明事务外副作用不存在。引擎事务未共同加入、缓存提前可执行或Job提前执行均停止A，不自动B/C。保护解除不能只“审批”覆盖事实，须只读确认一致、必要修复协议另审并维护窗审计。

## 7. 生命周期与控制信封

CREATE原子唯一插D再读真实值，两个实例赢家LIVE/rev0，输家按同控制键/异键裁决。RESERVE先则CREATE锁已有RESERVED，CREATE先则异键RESERVE已存在不重复预留。CREATE commit丢响应只原键查，不用新key重建。DELETE与T3/P3先D者优先，后者重验。

RESERVED无正文被删除后不可恢复、改IDCREATE是协议明确选择，需产品确认，不是未定义状态。rev/epoch最大值检查应在任何业务/部署副作用前，溢出整体不变；若P3部署后才发现rev溢出，A可回滚但仍需提前检查减少风险。

RESTORE使disabled/published0、active空，旧回执查询不复活资格。缺口：PUBLISH业务version不能采用current published+1，因为RESTORE把published清0。当前Runtime312正是published+1；正式迁移必须另有独立单调分配规则，否则唯一键冲突/旧映射复用风险，见P1-02。

控制信封列表只给参数名，未规定targetRevision/targetEpoch/observedEpoch是否统一十进制字符串，takeover为bool还是对象、reason是否可空；不能视为完整跨语言schema。CREATE/RESERVE的schema1及初始空文档默认版本也应提供固定示例。

## 8. Canonical静态复算

使用Python标准库hashlib对文档三个inputHex解码后的字节做SHA256，结果与三个sha256逐一相等。只读静态规格计算，不是Java/浏览器互通或业务schema验证。首个SAVE黄金明确为canonical测试schema，不得误认为可直接通过真实WorkflowModel校验。

三域与operation同时入信封、跨op同UUID独立已明确；null/缺失/空串区分，数值大精度、UTF8、标量序及历史版本兼容原则可保留。现有WorkflowJson8–11只Map排序，无严格重复key、Unicode和BigDecimal canonical实现。正文永久保存的隐私风险待审批。

缺少完整control黄金域+规范字节+摘要，参数schema不足；33项继承覆盖为文字向量而非完整测试包。建议补控制实例、跨操作同key、极端128/129、±1000指数、Unicode scalar边界、含反斜杠/引号的严格JSON输入/期望字节。schema有效性与语义等价不能混用。

## 9. 授权和HA

协议T1/T3分别授权，Session不授业务权，恢复器不替用户执行业务。最终判定前可见撤权拒绝，判定后并发撤权宽限须安全批准；“检查中”若分多次远程读取需定义权威视图，不假定缓存实时。

查询不用原publish执行权但需对象结果读取权是明确选择，返回回执应裁剪正文/人员信息；不能因key已知泄露。tenant/actor绑定由可信上下文而非body。

单主/不丢确认commit/旧主fencing/主库当前读是外部假设；DataSource配置不能证明HA。WRITE_PROTECTED触发和解除跨实例传播需运维机制，不能仅一个JVM布尔锁。现有代码没有该协议保护实现。

## 10. 当前源码核查范围

已确认生产入口Controller.save88–92/publish96–99/disable104–107，Runtime对应263–368；Definition写SQL集中于三方法。生产RepositoryService检索只定位Runtime.publish的createDeployment及processDefinitionQuery；FlowableMySqlTest为测试。Worker16–22维护实例job，未见定义保存写；没有定位其他生产删除恢复接口。外部SQL、运维工具或独立部署服务无法由仓库搜索完全排除。

当前mutate先锁旧wf_request再work，不是D→S→W；V1和内部Runtime未关闭，全部条件证明均不适用于当前正式运行。任何后续实现需源码门禁+真实负向调用验证，不能只关闭Controller。

## 11. A–I裁决

| 命题 | 线性化点/完整前提 | 本轮裁决及反例/验证 |
|---|---|---|
| A | 新T0commit，T3同D持锁当前epoch/Session | 条件性可推导，当前V1迟到保存反例；需MySQL多实例/HA |
| B | T3/T4终态commit，W当前读CAS | 条件性可推导；副本/旧缓存违规反例需实验排除 |
| C | 同全主键正文+回执commit，唯一领取/永久墓碑 | 基本定义充分；历史重放顺序P1-01及规范schema待补，需故障幂等 |
| D | 屏障T0commit，全部旧W终结并epoch失效 | 资格拒绝可推导，账本“全部终结”P0-01缺Definition范围；不得标通过 |
| E | 所有写完整D锁至commit | 跨类设计改进，真实锁序P1-04/当前旁路仍在；非全局串行化 |
| F | P3commit与新T0互斥，最终publish授权/A原子 | 条件证明，A未运行验证，B/C禁用；旧V1publish为当前反例 |
| G | 部署+映射+回执同commit/唯一版本 | P4矩阵P0-02和恢复后version分配P1-02不足；需Flowable故障 |
| H | GET D共享锁保护区至commit，所有写排他D | 条件定义清楚，V1可穿透为当前反例；需共享锁/路由实验 |
| I | DELETE/RESTORE T0commit、永久epoch/墓碑、disabled | 基本条件性成立，历史请求枚举及执行版本准入需验证/产品批准 |

没有一项把未验证条件性推导标作真实PASS。产品审批不能替代事务实验，实验也不能替代权限/数据保留批准。

## 12. 下一阶段门槛与操作记录

允许继续实验规格细化，尤其P4矩阵、逐主键锁序及控制黄金包；不允许按现稿开展完整隔离协议实现/验收，不允许正式代码改造或上线。先定向修订本报告问题，再独立复审。A原子性只能由隔离Flowable/MySQL故障实验获取，不建议先上线补偿。

开始Git status267项，既有文件哈希/tmp/v23-review-baseline.json。仅新增本文；结束哈希核查见交付回复。实际执行：只读git状态、文档/源码检索、三组摘要静态复算、哈希比较；未执行测试/构建/服务/数据库或安装依赖，未改既有协议/报告/Demo。
