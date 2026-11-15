# Workflow Designer 后端正式保存协议 V2.1 反向架构评审报告

日期：2026-11-15；结论：**B 修订后复审**。仅静态反向评审；不修改既有文件、不运行测试或数据库。V2.1不是正式系统已有实现。

## 1. 整体判断及V2问题关闭

已由文档定义：控制行原子插入、主库锁定当前读、终态CAS、延迟终结CAS、完整控制信封、顶层事务和批量上限，实质修复V2主要歧义。当前未发现能在“同一主库稳定控制行+所有最终写持锁至commit+当前读epoch/status”完整前提下推翻A的时序。

仍存在阻止整体协议通过的问题：RESERVED/LIVE与首次正文提交关系不闭合；GET/draft没有具体一致性事务；发布仍为未封闭的正式旁路；规范字节与UUID存储/比较实现不完整。结论B，不应因条件证明正确就批准正式改造。

| V2反向问题 | V2.1处理 | 本轮判断 |
|---|---|---|
| 首次控制行竞争 | 第3节唯一插入+当前读 | 文档解决锁入口，生命周期未闭合 |
| 旧terminate误伤 | 第4节sid/epoch/状态CAS | 文档明确，待多实例验证 |
| RR覆盖成功终态 | 第8节当前读+终态CAS | 文档解决，待MySQL验证 |
| 幂等控制键 | 第6节信封冲突/历史有效性 | 文档明确，字节存储未闭合 |
| 事务传播/死锁 | 第9/10节顶层、整回滚 | 文档明确，当前实现未采用 |
| 原子撤权/HA | 第11/14节宽限与写保护 | 非技术关闭；待安全批准/基础设施确认 |
| V1旁路 | 第13节要求关闭 | 当前仍存在，不得认定解决 |

## 2. 源码事实和边界

以下为已由源码确认：

- WorkflowController.java 88–107的save/publish/disable均直接调用Runtime；200–235处理可信上下文、幂等头与响应。文件在backend/services/workflow-service/adapters/flowable/src/main/java/com/appnexus/workflow/adapter/flowable。
- WorkflowRuntime.java 263–293保存model/revision；297–349发布；353–368停用；1300–1331的mutate先插/锁wf_request再执行work。均无控制行/Session epoch。
- WorkflowEngineConfiguration.java 29–53将注入DataSource用于JdbcTemplate，并传入PlatformTransactionManager；80–87给SpringProcessEngineConfiguration设置同样注入的二者。这是同装配参数证据，不是运行连接绑定和部署事务原子性证明。
- WorkflowJson.java 8–11仅ObjectMapper+ORDER_MAP_ENTRIES_BY_KEYS，未配置重复键严格检测或V2.1 canonical规则。Controller已将body解析为对象后无法恢复被解析器吞掉的重复键。
- V48__workflow_application.sql 3–19、47–51只有现有定义/发布快照/旧请求表，无V2.1实体。V69增加发布及实例数据契约快照，不提供编辑Session。
- Runtime.definitions 196–201为tenant过滤LIMIT100；不是新GET/draft。Runtime.definition 811–820从冻结wf_version读实例模型。
- WorkflowWorker.java 16–22调用实例维护/交付，未发现定义保存任务。全backend生产SQL搜索只定位save/publish/disable写wf_definition；BusinessObjectIntegrationTest175–177为测试旁路，不当作生产接口。

未发现生产删除/恢复定义或独立启用接口；发布会启用。未运行服务、读取数据库环境秘密或执行迁移。主从路由/实际隔离/外层传播需真实验证，不能把其他服务的READ_COMMITTED配置推导到工作流。

## 3. Definition生命周期缺口（P0）

V2.1第3节称创建/删除/恢复全为T0，RESERVED不能被普通激活悄悄转LIVE；第2/9节又要求无正文时可预留并首次T3保存。没有规定RESERVED会话是否可激活，以及哪个事务保证LIVE与正文对应。

反例L1：T0先设LIVE但未建正文，T3之前进程失败，最终LIVE无正文；如果不允许无正文则不变量破坏。L2：T3先插正文revision0但没有控制状态转换，最终RESERVED有正文；普通激活拒绝导致新定义不可用。L3：T3正文提交后单独T0设LIVE，中间删除/接管使状态与正文归属不一致。

最小修订二选一，不能混用：推荐T0 CREATE同时创建合法空草稿revision0并设LIVE，首次用户保存expectedRevision0；保留RESERVED仅作不可编辑预留，CREATE失败保持RESERVED。若产品必须首存-1，则明确允许RESERVED专属创建Session，并把首次正文插入+RESERVED→LIVE+请求SUCCEEDED放同一T3，承认这是第3节“全T0”的受控例外。

删除/恢复须规定正文保留、恢复哪个冻结来源、revision永不重置及已删除Session/请求回执查询语义。control epoch永久延续已定义，正文revision是否恢复旧值未明；删除/重建恢复旧revision可能使缓存误判，需单调策略。重复CREATE/DELETE/RESTORE同键返回历史+当前状态，未知commit查控制回执，不自动重建。

## 4. 保存与发布安全边界（P0）

| 路径 | 当前锁/写入 | 结论 |
|---|---|---|
| save | mutate先锁旧请求，再UPDATE定义 | V1旁路，须关闭或完整纳管 |
| publish/re-publish | 锁wf_definition，校验revision，compile→Flowable.deploy→插wf_version→更新定义revision/enabled | 不锁控制行/epoch，旧Session能发布，须关闭 |
| disable | 条件UPDATE enabled/revision | 无epoch，须关闭或纳管 |
| 内部Runtime公开方法 | 可直接被装配调用 | HTTP关闭不足，服务层也要封闭 |
| 后台维护 | 已定位实例job，无生产定义写证据 | 新增写须纳管；外部脚本不可由静态搜索排除 |
| 迁移/修复 | schema迁移不走在线协议 | 必须维护窗写保护，不能认为协议已覆盖任意SQL |

时序P1：save和publish同rev10竞争旧wf_definition锁，先者改变rev导致后者冲突，只提供版本竞争，不提供Session失效。P2：V2接管控制行升epoch8与V1 publish锁定义互不排他，publish仍可在新资格后修改enabled/revision，破坏“全部定义写排序”的前提。

部署成功但业务DB失败：若真正同一Spring事务连接并加入同一事务，预计共同回滚；仅从setDataSource/setTransactionManager不能排除引擎传播/缓存/部署副作用，需故障注入。若引擎独立提交，会留下孤儿部署。反向情形业务提交而部署提交结果未知，必须按engine_definition/deployment映射对账，不能再发新键盲目重部署。

正式切换前save/publish/disable及内部写都必须封闭。保存V2.1可独立验证，但真实设计器保留可用publish要求独立发布协议：部署幂等、版本冻结、引擎事务/回执一致、未知结果对账、控制锁持有策略。不得用“发布另审”默许旧路径继续在线。

## 5. GET/draft原子语义（P1，激活前须明确）

V2.1第12节要求主库快照，却没有定义控制行、Session与文档来自同一时刻。反例R：读D epoch7→接管提交epoch8→读SessionB/正文rev11，返回混合信封；单独同一DB连接不足以排除此反例。RR普通一致性读可一致旧快照，但不能与FOR UPDATE当前读随意混用；更不能把旧快照currentValid=true称当前资格。

建议短顶层只读语义事务（需要锁故不强制DB只读模式）：主库SELECT控制D FOR SHARE，随后锁定当前读active Session，再SELECT正文FOR SHARE，固定D→Session→正文；所有写先D排他锁，因此返回document/revision/epoch/session/lifecycle属于同一保护区。不存在控制行404；DELETED按授权410；Session字段不对只读无编辑权用户泄露。明确MySQL版本支持的FOR SHARE与事务设置，真实验证兼容性。也可单条JOIN形成一致快照，但必须明确该结果为读取时快照而非资格保证。

读取期间保存/接管/删除等待D共享锁；读取commit后可接管，前端最终save仍需epoch检查，不能通过GET锁保留编辑资格。GET无权限授予；主库故障结果不明不开放可写会话。

## 6. canonical与幂等字节契约（P1）

V2.1已给严格规则但尚未给UUID和持久字节表示。Java需要在原始流解析阶段开启STRICT_DUPLICATE_DETECTION等匹配版本的能力，并配置BigDecimal/BigInteger与原始数字长度检查；现有WorkflowJson不能直接复用来证明合规。普通前端JSON.parse会丢重复键及超精度数字，客户端可把服务端作为摘要权威，但不得先舍入再称原payload等价。

建议UUID强制小写8-4-4-4-12格式、明确版本/variant接受范围，服务器解析后规范为16字节；锁排序使用固定无符号字节序，与数据库BINARY(16)一致，不用大小写不敏感VARCHAR比较。tenant/definition现有字符串也需固定二进制比较/排序，不能让数据库排序规则和应用顺序不一致。

持久化分开：原始payload可作为受限诊断LOB；规范JSON UTF-8用BLOB/VARBINARY保证字节；SHA-256 BINARY(32)、字节长度、canonical版本作为校验列。MySQL JSON可作查询投影但不能作为原始/规范字节权威，数据库会重排/规范数字表达。canonical排序应对Unicode标量实现，Java String默认UTF-16比较不等价，补充跨实现向量。

payload清理后的摘要+长度依赖抗碰撞，V2.1已承认；需负责人接受或永久保留规范字节。旧canonical解码器与版本存续不能仅写“永久支持”而不定义发布兼容门禁。以下是固定规范向量设计，不是已运行结果；片段使用允许该字段的测试schema，封闭生产schema未知字段仍拒绝。

| # | 输入/比较 | 预期 |
|---|---|---|
| 1 | {"b":2,"a":1} | {"a":1,"b":2} |
| 2 | {"a":1,"a":2} | 拒绝重复键 |
| 3 | {"o":{"k":1,"k":2}} | 拒绝嵌套重复 |
| 4 | {} 与 {"a":null} | 不同规范字节 |
| 5 | {"a":1} 与 {"a":1.0} | 同规范数字1 |
| 6 | {"a":1e0} | 数字1 |
| 7 | {"a":-0.0} | 数字0 |
| 8 | {"a":1.2300e2} | 数字123 |
| 9 | {"a":1e-3} | 数字0.001 |
| 10 | {"a":9007199254740993} | 精确保留；JS Number不能先舍入 |
| 11 | {"a":NaN} | 拒绝非法JSON |
| 12 | {"a":01} | 拒绝非法数字 |
| 13 | {"a":"é"} 与 {"a":"e\u0301"} | 不同码点，不NFC |
| 14 | {"a":"\u0061"} 与 {"a":"a"} | 相同字符串a |
| 15 | {"a":"\ud800"} | 拒绝孤立代理项 |
| 16 | {"a":"\ud83d\ude00"} 与直接😀 | 同UTF-8码点 |
| 17 | 键U+E000与U+10000 | 标量排序E000在前，不能UTF16排序 |
| 18 | {"a":[1,2]} 与 {"a":[2,1]} | 不同，保序 |
| 19 | {"a":"\n"} | 控制码规范为小写四位u000a转义 |
| 20 | {"a":"/"} 与 {"a":"\/"} | 相同，不额外转义斜杠 |
| 21 | 数字0.后128个1（总130字符） | 超128输入长度拒绝 |
| 22 | 1e1001 | 指数超1000拒绝 |
| 23 | {"schemaVersion":1} 与版本2 | 摘要不同 |
| 24 | 同正文canonicalVersion1与2 | 不跨版本自动等价 |
| 25 | 相同UUID大小写文本 | 按新增UUID规则统一或拒绝大写，必须选定一种 |
| 26 | 同requestId不同epoch/actor | 冲突，不重新执行 |
| 27 | 未声明顶层字段、尾逗号、UTF8非法序列 | 拒绝，不静默丢字段 |
| 28 | {"a":"\u0000"} | 保留为转义，不在数据库字符串中截断 |

## 7. 确定性竞争推演

记D=控制行，S=Session，Q=Request，均主库FOR UPDATE当前读；顺序D→按字节序S→Q；commit为可见点。以下均静态推导，不是线程实验。

| 时序 | 事务/SQL/commit顺序 | 最终状态及判断 |
|---|---|---|
| 首次创建 | X/Y原子insert D同唯一键；X commit后Y读D真实值 | 一行；需验证死锁/回滚重试，不能用缺行锁代替 |
| 双激活 | X锁D读7，CAS8并commit；Y后锁D当前读8拒绝observed7 | 单有效Session；控制幂等规则足够时成立 |
| 激活/终结 | terminate先CAS关闭升8，activate observed7拒绝；反向activate先B8，terminate A7仅历史回执 | B不误伤，文档已明确 |
| 延迟旧终结 | D=B8，锁A历史，旧CAS不执行，不改D | 保持B8，若无条件UPDATE则反例，但V2.1禁止 |
| T1/接管 | T1先D插CREATED commit，T0后终结Q；或T0先后T1读失效拒绝 | 不漏迟到请求 |
| T2/T4 | 领取先Q PROCESSING commit，T4当前读CAS失败终结；T3晚到读FAILED | 无正文提交，保守取消可损可用性不破安全 |
| T3/T4 | T3先D写M1+SUCCEEDED commit，T4读成功不改；T4先FAILED commit，T3拒绝 | 排除成功改失败；必须整事务同源 |
| 成功丢响应 | T3 commit11，传输丢；原键query当前读成功 | 回执11，不再写 |
| 死锁/超时 | T3语句部分更新后SQL异常，必须整个事务rollback再查询 | 若catch后commit部分状态即违协议，需真实注入 |
| 64终结 | T0持D锁全部S/Q并CAS终态，一次commit；中途故障全回滚 | 不允许分批屏障伪成功 |
| 第65请求 | T1等D，当前读计数64，拒绝；若T0先终结则T1失效拒绝 | 注意拒绝回执应直接FAILED，不额外占非终态 |
| 删除/旧到达 | T0锁D升epoch/DELETED commit；旧T1/T3随后当前读拒绝 | 资格安全成立，正文生命周期/恢复规则待修 |

每条必须测试影响行数和终态CAS；父锁顺序不消除外键/索引/引擎的全部死锁。

## 8. 事务与数据库边界

已由源码确认：WorkflowRuntime tx默认TransactionTemplate(manager)，未在该类声明拒绝外层事务；现有save无D控制锁。V2.1要求顶层完整T0–T4，尚未实现。未发现工作流REQUIRES_NEW不代表运行时不能被事务调用方包裹。REQUIRES_NEW若包完整写事务可持锁，但外层先持其他锁可能死锁；短校验内层提前释放再外层写不合协议。

RR下锁定当前读用于终态决策已明确，但实际MySQL版本、InnoDB、连接池session隔离复位、主副本路由需真实验证。唯一键等待、deadlock victim回滚、lock timeout可能仅单语句回滚差别不能静态测试代替。commit未知只能权威查询，不能catch直接FAILED。

同注入参数不等于真实DataSource绑定证明；两实例误接不同主库时各自锁D均可成功，是HA反例，不由行锁修复。故障切换丢epoch/墓碑会复活旧请求，V2.1写保护需基础设施可观测、可强制旧主隔离机制。

## 9. A–E重新证明

| 命题 | 定义/前提/线性化点 | 反例与结论 |
|---|---|---|
| A | 新T0激活commit后，旧sid/epoch无新T3正文commit；同D锁至commit及当前读 | W先锁则在N前commit；N先则W拒绝。V1 publish/save旁路为当前反例；V2.1条件性成立 |
| B | SUCCEEDED不被T4覆盖FAILED；D→Q当前读、CAS和同事务 | RR扫描不参与决策，当前读见成功；T4先则T3拒绝。跨数据源/副本反例须配置排除；条件性成立 |
| C | 同tenant/definition/saveRequestId最多一次正文提交 | 唯一键、T2单领取、T3成功CAS+正文原子、永久墓碑。摘要清理/UUID别名不明影响幂等实现，需字节契约；条件性成立 |
| D | 屏障commit后目标旧Session已到达/未到达请求无资格 | T1/T3当前读旧epoch拒绝；CAS不重置。恢复同ID若重置epoch为反例，文档禁止；生命周期需闭合；条件性成立 |
| E | 同定义所有有效写完整持同D锁，事务产生串行顺序 | 不等于整个系统serializable隔离；控制锁外Flowable/V1写反例。当前不成立，协议前提下成立 |

这些结论依赖V1关闭、所有内部写纳管、单主、事务同源和真实提交边界，均不是已实现事实。

## 10. 集成验证建议和必须修订

P0：闭合RESERVED/LIVE与首存协议；正式切换前关闭V1 save/publish/disable及内部旁路；明确发布未具备协议时不可用。P1：GET原子信封具体事务、UUID/排序/规范BLOB契约、幂等payload清理风险批准、事务装配门禁、授权宽限签字。P2：容量/恢复器扫描和诊断优化不得改变不变量。

真实MySQL验证沿用V2.1的24项并补充：首次CREATE/首存崩溃各切点；D/S/正文混合快照反例；draft持共享锁与保存/接管/删除；UUID二进制排序跨Java/MySQL；规范JSON入JSON列与BLOB字节对照；实际Flowable部署回滚/commit未知；V1关闭后的内部调用绕过。每例取正文/revision/epoch/S/Q/引擎部署快照，以SQL屏障确定顺序，不以sleep/线程启动顺序作证明。

静态可确认接口/SQL路径及规则存在；MySQL确认当前读、锁保持和原子更新；多实例确认激活/恢复竞争；故障注入确认commit丢回包、崩溃和批量回滚；HA确认单写/不丢确认提交；产品/安全批准独占接管、撤权宽限和摘要清理风险。以上均未执行。

## 11. 最终决定与工作区保护

结论B修订后复审。可允许独立canonical向量设计及非验收性质的数据库语义研究，但不批准按现稿进入完整隔离协议实现/验收；先解决P0及关键P1后再评审进入真实MySQL集成验证。不得开始正式后端改造，不批准上线，不替换bpmn-js或引入X6。

本轮开始git status有262条；状态与所有既有文件SHA256记录/tmp/v21-review-baseline.json。仅新增本文；结束复核结果见交付回复。未运行构建/测试/服务/迁移，未改代码、配置、依赖、已有文档和Demo证据。只执行git只读状态、文件读取、rg检索及新增报告/哈希检查。
