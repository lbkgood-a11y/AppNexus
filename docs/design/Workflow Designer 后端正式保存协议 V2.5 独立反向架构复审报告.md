# Workflow Designer 后端正式保存协议 V2.5 独立反向架构复审报告

日期：2026-11-15；总体结论：**B 修订后复审**。P0两项、P1三项、P2一项。本轮只读分析与新增本文，未创建实验/测试代码、未运行构建/服务/数据库。

## 1. 改进及证据分类

相比V2.4：G0持久VALIDATING能在合规写入口间保留崩溃封锁；410客户端待核验/禁止自动换key明确；边ID关联、core与派生XML、身份删除降级、Unicode集合及历史UUID分流都有实质进展。没有因回应旧评审就默认关闭。

文档规则：D/S/W、保护检查、G0/G1及新codec均为设计。源码事实：当前Runtime/Controller仍V1无保护字段。逻辑推导：合规同D锁前提下G0后无普通新写，但不证明当前系统所有入口合规。静态计算只核对向量，不是跨语言测试。MySQL/Flowable/HA和安全隐私批准均未执行/未获得。

## 2. R01–R05复审结果与问题清单

| 编号 | 级别/章节 | 具体反例与风险 | 最小修订/门槛 |
|---|---|---|---|
| N01 | P0 §19门禁 | G0提交VALIDATING；旧Runtime.publish仍只锁旧wf_definition并deploy，start路径也只读enabled。旧实例/直接RepositoryService可产生部署或启动，不读D。文档门禁是要求，不是当前满足事实 | 全入口能力封闭清单+服务层禁止旁路、部署/启动保护契约；当前正式实施BLOCKED，先规格闭合而非假设未来接入 |
| N02 | P0 §19控制状态 | G0commit后操作者撤权/进程退出，G1不能合法重入；恢复器只可相同token或WRITE_PROTECTED，但无恢复/接管/取消接口、授权、回执、token归属与CAS。最终Definition可能永远VALIDATING，只能手工SQL | 定义受控RECOVER/RESUME/ABORT控制操作及token绑定/审批/审计/不变式；禁止直接SQL解封；不要求自动可用性但必须可管理恢复 |
| N03 | P1 §17.5/§10 | schema protocolVersion固定"2.4"，控制黄金JSON为"2.5"且域v2.5；同新请求既可schema拒绝又被黄金接受 | 新请求唯一2.5；历史2.4只走原记录验证，不混同新schema；重算/明确版本黄金 |
| N04 | P1 §19 G1 | G1只写“同key/token”，未规定token绑定t/d/controlOP/targetRev/epoch、当前权限和目标重新校验；若token复用/管理员重入改变参数，可删除错误目标 | token不可变完整绑定与主库CAS，重入当前授权/目标复核，PENDING_VALIDATION状态转移与响应查询字段完整化 |
| N05 | P1 §21 | core schema/codec新增但SAVE仍§6 parameters.document旧平面表述，CREATE输出document={core,...}；XML样例无name，末尾特判id缺省；完整通用codec映射和导入安全未定义。modelDigest/coreDigest术语及XML生成确定性仍需收敛 | 单一document schema/API绑定、版本化codec规则/名称映射/namespace descriptor、CREATE失败路径一致，不以模板特判代替所有文档规范 |
| N06 | P2 §22/23 | 身份全删除后仍要按历史owner授权，但替代标识/墓碑合法保留未批准；reason零宽字符可全不可见但合法 | 明确拒执行不披露历史分支、隐私审批和UI可见性策略；非核心锁证明反例 |

R01在合规封闭世界内修复“回滚后再保护”窗口，但N01/N02/N04阻止判关闭；R02文本基本关闭，待客户端/审计实现；R03边ID改进但新schema面N05未关闭；R04选择不可查询仍拒执行，合规无法保留墓碑时明确阻断可保留；R05集合明确，历史兼容方向合理，版本冲突独立N03。

## 3. G0/G1故障状态表

| 阶段/故障 | 持久状态/可推导行为 | 不足或恢复要求 |
|---|---|---|
| G0获取D前/锁超时 | 无准备成功事实，不启动G1 | 可以同控制键重试，不能宣称已屏障 |
| G0验证失败 | 应无VALIDATING，拒绝 | 需完整规定失败回执是否持久及幂等 |
| G0commit成功丢响应 | D=VALIDATING/token，控制PENDING_VALIDATION | 主库按原key查询必须返回同token，字段与授权尚缺 |
| G0commit未知 | 可能已VALIDATING | 不得NORMAL；主库未知保持封锁，HA不能猜 |
| G0后崩溃 | VALIDATING持续，普通写拒绝 | 安全停机但N02无可执行恢复契约 |
| G1获取锁超时/SQL失败/rollback-only/死锁 | G1整体回滚，G0仍VALIDATING | 不等于DELETE成功/回滚整个协议；需受控恢复 |
| G1普通业务前置异常且事务可提交 | 可写WRITE_PROTECTED+FAILED回执，无DELETE业务 | 需分类稳定错误，不能对SQL异常强行提交 |
| G1业务后校验异常 | 可选savepoint回滚业务后写保护 | MySQL/JDBC/Spring锁/rollback-only待验，首版不应依赖该分支 |
| G1保护commit未知 | 可能WRITE_PROTECTED或仍VALIDATING | 原key查，不直接NORMAL，不新token |
| G1成功commit丢响应 | DELETED、回执SUCCEEDED、NORMAL | 同原key复用；需先查终态再检查VALIDATING，否则已完成重试误拒 |
| 数据库不可达/分区 | 无权威读不允许新写 | 依赖旧主fencing及全实例fail-closed，不是单JVM标志 |

G0 commit前不授准备资格定义正确。G1应先检查终态回执再匹配token，且须token与target完整绑定、current权限重新核验。原G0后的所有其他写都被拒，故target不应被合规写改变，但外部修复/历史异常仍需复核，不能省略。

## 4. 全入口保护矩阵

| 入口 | V2.5规则/源码事实 | 判定 |
|---|---|---|
| SAVE/PUBLISH/DISABLE T1/T2/T3 | 文档D当前读保护；Runtime263–368无D/保护 | 未实现，不得判合规 |
| CREATE/RESERVE/ACTIVATE/接管 | §19禁止VALIDATING普通写；新ID初始化保护默认未在表中明确 | 协议要求，需默认NORMAL与初始化CAS |
| TERMINATE/RESTORE/其他DELETE | 文档只匹配token G1例外，其他禁止 | 初始安全，恢复操作缺N02 |
| T4/P4 | 普通T4禁止；P4矛盾只保护 | P4在VALIDATING时只读核查/保护写如何准入需明确例外，不许恢复NORMAL |
| Runtime内部save/publish/disable | Controller关闭不足；当前公开方法可被内部调用 | 必须服务层封闭，当前旁路 |
| Flowable RepositoryService/deploy | Runtime317–324直接调用；没有保护检查 | 必须专属受控能力，直接调用负向门禁 |
| 新实例start/目录匹配 | Runtime约32–34及442–445仅enabled，504启动 | 文档§19清单没有完整启动D锁/保护契约，不能声称VALIDATING期间无执行副作用 |
| 既有实例/后台job | Worker16–22→dispatch/maintain | 是否冻结既有实例必须区分；不能把全部运行时一律封死，需产品规则，默认冻结定义不等于停止实例 |
| 缓存/事件/旧应用实例 | 必须主库当前读、不接受缓存NORMAL | 上线版本共存/全实例切断需运维证据 |
| 维护/保护解除 | 双角色审批原则存在，无具体API | 不能允许管理员直接SQL清标志作为协议恢复 |

因此不能证明“G0已commit⇒所有实例必然遵守”。可证明的是：同D锁且每次主库检查保护的已纳管入口，在G0后不能普通提交；当前代码不满足完整前提。

## 5. 全历史DELETE与锁序

§17.1全历史S、所有epoch非终态W、逐完整PK锁及归属复查是改进；64仅非终态W，不是所有历史S。RC枚举期间D阻止合规新增，不依赖ORDER BY物理顺序。缺S/多ACTIVE/epoch错配/超64进保护，前提是N01旁路关闭。

潜在锁环：旧V1 mutate锁旧请求R后等正文B；新G1 D→S→W→B并通过兼容/审计代码反向等待R，可形成R→B与B→R；逐PK遍历不能消除外键/二级索引/旧代码锁环。另一例维护任务先W再D与G1先D再W相互等待。SQL结果序、应用遍历序和InnoDB隐式锁序必须分别取证，需EXPLAIN、performance_schema和故障实验，不可只文本保证。

## 6. 幂等重放与隐私

正常A成功B接管：历史读取权+相同信封先于当前资格，可回原终态，不能FENCED重判。PUBLISH重放/GET需先P4一致性检查：FAILED有部署、CREATED有部署、PROCESSING有E/M、SUCCEEDED缺任一、多部署/错配均保护不回正常终态；非终态不擅升成功，矩阵原则完备但未运行。

body清理后摘要+长度只抗碰撞等价，未批准410而不是未知请求/新CREATED。§20客户端RECEIPT_UNVERIFIABLE保原key、GET/人工、不自动新意图已补齐。DELETE控制410同样需走control GET而非W GET，路径对应应写清，不能把所有控制请求塞W。

身份清除后最小墓碑拒执行与无权读取可并存；新账号不因key相同继承回执。若必须连可拒旧键的依据都删除，协议承认合规阻断，没有证明通用替代机制；不能宣称已解决隐私与全部历史可查。永久关联摘要/epoch仍可能属个人信息，需隐私批准，不自称匿名。

## 7. 版本、schema和静态字节

确认§17.5仍写protocolVersion固定"2.4"，六控制黄金请求2.5及域v2.5不是历史示例，属于规范矛盾；同字节仅换版本摘要必不同，新请求schema和域必须唯一。旧2.4回执应按记录旧域验证，不重算覆盖，不能用兼容解释新schema冲突。

CREATE新增model.edges.id与diagram/BPMN id统一有效，core模型语义/DI权威分工基本清楚；但完整文档wrapper、派生XML生成及SAVE schema升级需要一致定义。XML样例靠末尾id缺省name约定避免不一致，非通用名称映射证明；实体/DTD/外部资源、未知扩展与codec描述仍需完整规范和实验。CREATE生成失败同T0回滚可保持无半LIVE，实际编解码器当前不存在。

next_version设计最大可分配MAX-1、MAX哨兵合理，A共同事务回滚计数才可复用；若孤儿部署残留则禁止A。当前Runtime312仍published+1，明确不兼容旁路。

九组黄金域+inputHex+hash可进行静态复算，不能消除版本schema矛盾；本轮未执行Java/浏览器互通，也不因字节哈希一致判codec通过。

## 8. Flowable A、权限及HA

WorkflowEngineConfiguration29–53/80–87同DataSource/TransactionManager仅配置事实；部署是否实际同连接/传播、缓存/Job/监听提前副作用必须故障实验。A仍候选，B独立提交/C补偿未获批准；不允许保护期间旧Session经直接引擎API造孤儿。

权限目录/撤权宽限/授权缓存、历史查询裁剪需安全产品批准。G1原actor撤权后是否允许恢复操作必须单独角色授权，不让恢复器替用户完成DELETE。HA单主/不丢确认commit/旧主隔离/全实例保护传播均外部前提，应用D锁不证明双主安全。

## 9. 下一阶段及R01的证明范围

必须先补：全入口准入（尤其定义新实例与直接引擎）、G0/G1 token schema和可审计恢复状态机、版本一致性、统一document/codec契约。允许继续只读规格细化，暂不进入隔离实现/实验或正式改造。没有发现必须推翻D锁/W账本的根本反例，因此B而非C；没有未决P0/P1关闭，因此不选A。

必须实验：G0commit丢响应/崩溃、G1全部SQL/commit异常、savepoint、全入口负向调用、MySQL历史枚举、Flowable A/P4、跨实例缓存及HA。外部批准：保护恢复角色、撤权后恢复权限、实例运行/启动语义、隐私最小化及保留、运维解除审计。文档定义不是实验通过。

## 10. 工作区保护

开始git status273项；基线/tmp/v25-review-baseline.json。只新增本报告；原协议、源码、迁移、Demo、配置、测试不写入。未安装依赖、创建实验代码、运行服务/数据库/测试/构建，未清理或恢复外部修改。结束哈希核对见交付回复。
