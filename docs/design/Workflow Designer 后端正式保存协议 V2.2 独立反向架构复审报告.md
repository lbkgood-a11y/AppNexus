# Workflow Designer 后端正式保存协议 V2.2 独立反向架构复审报告

日期：2026-11-15。结论：**B 修订后复审**。问题统计：P0两项、P1四项、P2一项。本轮仅静态检查及新增本文，不运行任何测试、构建、服务、依赖安装或数据库操作。

## 1. 总体判断与证据分类

V2.2显著改善CREATE/首存、延迟terminate、主库当前读和GET锁区定义。但disable并未形成完整可实现协议，请求类别扩展与终结/恢复表的统一性不明确，摘要域也存在解释空间。不能仅凭“同保存协议”证明新增写路径完整。

分类：下文“协议”指V2.2规定；“源码”指当前生产实现；“推导”是前提下逻辑分析；“待实验”指MySQL/Flowable/多实例/故障；“待批准”指产品/安全/运维。

V2.1问题关闭：CREATE同T0空草稿rev0、GET D共享锁区、UUID二进制和规范BLOB均已由协议定义；实现没有验证。发布V1关闭门禁明确，但发布事务只有准入条件、没有运行证明；B/C禁用可保留，不等于它们已安全。

## 2. 问题清单

| 编号/等级 | 章节及反例 | 对命题影响/最小修订 | 阻断范围 |
|---|---|---|---|
| P0-01 | 第18.2 disable仅一句同资格/幂等，无disableRequestId、信封、路径、状态/回执、恢复表定义；开发者可沿用旧mutate而绕过D | E及写路径闭合不完整；单独定义受理/领取/提交/查询及operation域，或首版明确禁用disable | 阻断完整协议实验，非禁止隔离数据库语义研究 |
| P0-02 | 第2/8/9节只定义wf_save_request_v21与save_request_id；18节引入独立PUBLISH类别并称屏障合计64，却未定义发布请求表/统一键/跨类型锁序及P4查询SQL；终结只扫描save表的实现会遗漏publish | D/F依赖的全类型终结和恢复不封闭；定义统一write_request(tenant,definition,operation,requestId)及统一恢复器，或给全部分表全序与扫描SQL | 阻断完整协议实验 |
| P1-01 | 第7节SHA域为workflow-save/control:v2.2，第20节笼统升级域，18节又用PUBLISH；save/control是字面还是两个域不明确，发布/停用未列 | C/G幂等互通风险；按operation明确固定域字节和完整信封，给黄金摘要向量 | 阻断互通验收，可单独研究canonical |
| P1-02 | 第3节正文生命周期SQL在CREATE未显式deleted=false联动检查；RESTORE未规定enabled/published恢复策略。曾enabled的定义恢复后可能直接接收新实例 | 不必推翻I的旧编辑资格，但业务发布/执行资格可能复活；定义恢复默认disabled、显式启用策略及Runtime匹配过滤 | 产品/安全批准前阻断恢复实施 |
| P1-03 | 第18.2 P3列资格/版本/PROCESSING但未逐项写最终publish权限校验；17/22节称所有写适用授权时间语义，仍需明确P3执行publish权限而非save权限 | F是会话资格不等于授权；P1/P3/查询明确独立操作授权与撤权判定，禁止靠会话授权 | 发布实施前关闭 |
| P1-04 | 第18.3以矛盾映射WRITE_PROTECTED为恢复，未定义P4发布终态判定/部署缓存清理与对账唯一观察，且A路径原子仅假设 | G不能静态判通过；提供A资格实验门槛及失败停止规则，明确P4对引擎/映射/回执异常不写FAILED | 发布实验须先有明确规格 |
| P2-01 | CREATE/RESERVE/DELETE/RESTORE控制API未逐项列信封及返回，revision溢出只有epoch溢出条款；33组向量多为语义片段无完整黄金字节 | 生命周期及互通实现差异；补操作参数表、revision上界及完整测试包 | 不单独推翻A，正式实现前明确 |

上述是协议定义不足，不是当前代码中已执行的安全漏洞。发布A路径暂不具备部署/上线资格；不能建议先上线再补偿。

## 3. 生命周期与锁序

协议第3节已选择唯一方案：CREATE同T0控制行+空正文rev0+LIVE；首存0。两个CREATE原子insert-if-absent争同唯一键，赢家commit后输家当前读LIVE：同控制键返回历史，异键ALREADY_EXISTS；回滚后等待者可创建。未知commit查原控制键，不以查无正文就换键。

RESERVED可删除但无正文不可RESTORE，只能新ID，是明确的不可恢复保留身份，不属于无法处理的悬空状态；需产品认可。DELETE同D锁关闭Session及请求、软删rev+1，RESTORE同源正文rev+1/epoch+1/active空，原则排除旧编辑资格复活。

RESTORE的enabled/published问题另见P1-02：旧发布版本不可变与是否重新启用是两件事。应明确Runtime发起匹配排除DELETED及disabled，不能仅草稿API软删。

第3节CREATE先初始化D属于唯一例外，然后D→Session→Request→正文；第4节控制回执查重可在D后，但写回执顺序文字最后。由于同D互斥，单定义内未构造出仅由回执先后造成的死锁；跨定义禁用的规则可保留。若分表请求混合排序不统一，仍缺跨类全序，见P0-02。

永久墓碑防旧键复活已由协议定义；生产实现没有这些墓碑，不能称已满足。revision回绕需补明确拒绝，不能DB溢出后截断或重置。

## 4. SAVE确定性事务推演

记D锁为主库FOR UPDATE，S/Q均锁定当前读，锁持至真实commit。

1. **Worker与恢复器**：T2提交PROCESSING；W先D→S→Q，T3正文+SUCCEEDED提交；R随后D→S→Q读SUCCEEDED，CAS不改。反向R先CAS FAILED commit，W后读FAILED不能执行。RR候选快照仅选ID不参与判定，旧快照反例被第8节排除。待真实RR与commit未知实验。
2. **Worker与接管**：W先D时N等待，W commit后N终结旧资格；N先D commit epoch8，W获得锁当前读7失效，无法写。T1已受理不授T3资格。当前V1无epoch不满足此时序前提。
3. **不同类型请求**：SAVE S与PUBLISH P都expectedRevision10；S先D写rev11，P后D读11拒绝；P先D部署+映射+rev11，S后读11拒绝。只在发布A真正同事务、权限及表类型定义完整时成立；类型分表终结/恢复缺失不能据此忽略。

同键T2条件领取唯一，PROCESSING不启动第二worker；原worker迟到在T3重新检查。死锁/超时必须整体回滚而非只语句回滚，未知commit先查询；不能根据网络错误盲写FAILED。64总上限需要统一请求类别计数，第65同D读计数拒绝，若只count save会超额并漏结算，见P0-02。

## 5. 发布与Flowable原子性

源码：WorkflowRuntime.publish 297–349在旧mutate中锁wf_definition，校验model/revision，compile→engine.repositoryService.createDeployment.deploy→query processDefinition→插wf_version→更新published/enabled/revision→成功response。WorkflowEngineConfiguration 29–53、80–87将注入DataSource/PlatformTransactionManager分别用于Runtime和SpringProcessEngineConfiguration。这只能证明配置传参，不能证明运行同连接、传播或故障原子。

协议A要求P3从D锁持到部署及业务真实commit，source_revision固定、版本/发布key唯一、请求终态原子，可排除旧Session接管后产生业务发布版本。还需核验引擎事务拦截器、事务监听器、部署缓存提交时机、异步Job可见性和外部监听副作用。未取得此证据前PUBLISH不具备实施资格；允许制定验证方案，不是启用发布。

反例：deploy内部独立commit后业务INSERT失败，留下deployment而无wf_version；重试若只有业务请求FAILED而无引擎幂等，可再部署。V2.2明确该实验失败则禁用A是正确门禁，不是反例已被运行排除。反向DB映射已commit而部署结果丢失，若同资源原子可query回执；若部署根本独立资源，回执不能证明引擎正确，需写保护对账。

P3与接管/DELETE争D排序只覆盖事务内发布。B/C禁止后不能保留V1或内部Runtime入口，否则旧Session仍能deploy；HTTP禁用不足，服务层、任务、修复工具及直接repository调用均需门禁。disable完整协议见P0-01，不能靠publish规则自动补全。

## 6. GET /draft与H

第19节MySQL8+/InnoDB、READ COMMITTED短顶层事务，D FOR SHARE→授权Session FOR SHARE→正文FOR SHARE；这是当前读，不是三个互不相关普通快照。若所有写先D排他，则共享锁保护区不变：GET先锁时SAVE/接管/DELETE等待；写先commit时GET后读新组合。GET后马上接管正常发生，不推翻H；前端不能把读回currentValid当长期资格，最终写复核epoch。

当前源码Runtime.definitions 196–201只是列表SELECT，无D/S结构；不是上述实现。任何V1不锁D的UPDATE可以穿过共享D锁，GET先读D旧资格、后读新正文形成混合，故V1封闭为H必要前提。请求只读commit失败503重读已定义，不能用副本结果开放写。

权限检查及tenant过滤必须在读返回前，active owner等敏感字段按读者权限裁剪。共享锁本身不提供权限。实际MySQL版本/驱动/事务路由与连接池隔离复位均待真实验证。

## 7. UUID与Canonicalization

协议第20节小写v4/variant、网络序BINARY16、unsigned byte排序、definition二进制比较已明确。现有定义使用VARCHAR，迁移碰撞/关联表一致性待真实检查；不能只给新控制表换binary就忽略旧表唯一键排序。

WorkflowJson.java 8–11只启用Map键排序，当前不是严格canonical解析器；Controller对象绑定前原始重复键检测、BigDecimal/BigInteger、UTF8/代理项检查尚未实现。Java UTF16排序不能替代标量排序，协议已指出。数字原始128字符及指数/展开范围可实现但需跨语言向量，JS Number提前解析会损失大整数。

第6节保留“将来清payload仅摘要+长度”的风险选择，第20节首版永久canonical且禁用清理是更严格选择，可按首版解释，但应删除实施歧义。raw仅诊断、canonical BLOB为摘要权威、MySQL JSON仅投影是可保留设计。摘要域P1-01：不能让实现者自行推测publish属于save域还是新增域；操作身份同时进入域与信封，并给域字节十六进制黄金向量。

33组向量不覆盖完整签名信封、UTF8键排序字节、反斜杠/引号组合、恰好128/129边界、指数-1000/+1000、同control key异操作、publish/disable跨类型同UUID。需要补充且区分语义等价与完整schema有效性。向量只是规格，未执行。

## 8. V1与内部入口核查

已由源码确认：WorkflowController.save88–92、publish96–99、disable104–107；Runtime.save263–293、publish297–349、disable353–368；mutate1300–1331先锁旧请求，均无D/Session/epoch。生产SQL检索Definition写集中于三方法；引擎createDeployment生产检索在publish，FlowableMySqlTest为测试不是生产旁路。WorkflowWorker16–22为实例维护/交付，不见定义草稿写；没有定位生产删除恢复接口。

V2.2第18.3明确HTTP和内部路径全部关闭或受控委托，此门禁协议完整但实现未有。应静态架构规则+服务层负向调用+真实DB审计验证；单纯搜索没有命中不是绝无外部SQL证据。迁移修复必须维护窗写保护，不把DB管理员任意SQL纳入在线安全证明。

## 9. A–I逐项裁决

| 命题 | 线性化点/前提及文档完整度 | 反例、当前代码与待验证 |
|---|---|---|
| A | 新T0 commit；所有T3同D锁当前资格，保存规则基本明确 | V1迟到保存反例当前存在；条件推导成立，待多实例 |
| B | T3/T4终态commit；当前读+CAS明确 | 不用RR快照可排除误覆盖；当前无V2恢复器，待故障 |
| C | 正文+成功回执commit；唯一键/领取/墓碑 | 保存基本明确，跨操作域/类型需修；待真实幂等 |
| D | T0终结commit；旧epoch失效，所有类型资格校验 | save定义明确，publish/disable扫描类型未闭合P0-02；未通过 |
| E | 同定义D排他锁至commit | disable和全写路径未闭合；当前V1无D，不成立；不外推全局事务 |
| F | P3业务版本commit与T0排序 | 发布A原子和最终权限为前提，未验证，B/C禁用；不得称通过 |
| G | P3映射/引擎/回执同commit | 配置相同不证明原子，孤儿部署反例待排除；未通过 |
| H | GET共享D锁保护区到commit | 文档明确；V1可穿透反例需关闭，待MySQL |
| I | DELETE/RESTORE T0 commit，永久epoch/墓碑 | 编辑资格条件性不复活；enabled恢复业务资格待修；待生命周期故障 |

“未通过”表示没有实证或规则尚缺，不等于已有执行失败记录；当前正式实现未具备V2.2实体，不能使用条件证明宣称生产安全。

## 10. 测试规格审查与补充

第15/22节真实DB时序、33组向量全部未执行。分类与缺口：

- 静态：全Runtime/Controller/内部引擎部署调用门禁、锁序、请求类别表与状态更新、事务边界拒绝。
- 单元：完整canonical黄金字节+摘要，UUID边界，control/save/publish/disable同UUID异操作，合法状态转换，所有错误回执。
- MySQL：CREATE原子首存、DELETE/RESTORE正文与enabled、FOR SHARE一致区、RR终态、64全类型总上限与65拒绝、冲突whole rollback。
- 多实例：同键双worker、跨请求类别屏障、PUBLISH/SAVE/接管/DELETE/disable竞争、控制回执未知结果。
- 故障注入：deploy后业务失败、commit回包丢失、P4读取不一致、缓存/监听提前暴露、批量中途异常、事务管理器/数据源错配。
- HA：状态查询主库、旧主隔离、确认commit丢失/双主触发写保护，应用只读降级不伪终态。
- 审批：空草稿与RESERVED不可恢复语义、RESTORE默认disabled、独占接管/撤权宽限、墓碑隐私和容量、主库保障。

新增必测：disable独立请求成功/失败/未知/重复/旧token；64条混合SAVE/PUBLISH/DISABLE；同UUID不同operation摘要域；RESTORE后未显式发布是否允许实例启动；引擎部署在回滚后缓存及Job可见性。不得只验Mock。

## 11. 进入实验的门禁与最终结论

B修订后复审：先关闭P0-01/P0-02并明确P1域及发布实验准入条件，再独立复审。暂不允许按现稿开展完整协议隔离实现/验收；可继续只读研究和实验规格准备，但不能自行运行本轮禁止的测试。

没有证明Flowable真实原子部署能力；A路径只能列待验证，B/C未批准，不能先上线再补偿。没有批准正式后端改造、设计器接入或上线，也不批准替换图引擎。

## 12. 操作与保护

开始Git status265项，基线/tmp/v22-review-baseline.json记录全部既有文件SHA256。仅新增本文。实际执行git只读状态、rg检索、完整读取协议、核对Runtime/mutate与部署引用、哈希复核；未构建、测试、安装、启动服务或操作数据库。结束哈希结果见交付回复。保留所有已有未提交修改。
