# 第三道门槛：异常注入与设计会话一致性

日期：2026-11-15；范围：独立Demo，无正式接入。结论：九个实际注入用例的会话隔离验证PASS；完整事务恢复未实现，方案A整体仍未通过。原生handler函数体内部故障BLOCKED，不能以生命周期故障替代其覆盖。

## 1. 实际文件与基线

新增src/failure-session.ts、tests/failure-injection.spec.ts及本报告；新增failure-*证据。无依赖、锁文件、正式模块、后端或六个外部变动文件写入。未reset/clean。failure-baseline.json保存执行前git status和八个前两道/旧断言文件SHA256；failure-integrity.json验证八个文件均未改变。六个外部文件未做本轮前后内容哈希，不能据此声称没有其他任务并发修改。

## 2. 环境与公开扩展

bpmn-js 18.31.1、diagram-js 15.29.0。Modeler additionalModules依赖注入、CommandInterceptor execute/executed/reverted事件及优先级；公开modeling.moveShape、commandStack.undo/redo/canUndo/canRedo、saveXML、elementRegistry和Modeler.destroy。测试配置由createFailureSession参数显式构造，仅测试动态加载，不接入App或生产入口。

复用第二道evidence/connected-initial.bpmn：一个移动StartEvent、一个固定UserTask、一条SequenceFlow；Bounds=(530.25,140.5,100.75,80.125)。原始路径[(631,180.5625),(700.25,180.5625),(700.25,180),(800,180)]。不增加类型、属性或复杂图。

## 3. 隔离策略

封闭会话不向调用方暴露Modeler/原始栈。所有允许的Move/Undo/Redo同步调用由operate捕获异常；catch首先poisoned=true，再只读采集诊断（包括saveXML重新解析），finally通过公开destroy销毁Modeler并移除DOM。后续Move/Undo/Redo/save/export均抛出SESSION_ISOLATED并要求从最后已提交文档重载。测试实际逐项调用验证拒绝，并验证画布宿主已移除。

诊断XML仅属于测试证据通道，含故障部分状态；不是用户导出、保存或提交。用户操作入口在采证之前已封锁。此Demo没有真实保存API或已提交文档仓库，因此只证明本地保存入口封锁，不证明服务端准入或重载流程。

没有恢复、修理或继续使用损坏历史。即使几何恰好一致仍执行隔离。完整恢复0项；会话隔离9项（D3拆为两个方向）。

## 4. 场景及精确状态

记O=(530.25,140.5,100.75,80.125)，T=(550.25,150.5,100.75,80.125)，R=(550,151,101,80)，M=(550.25,151,101,80)。均按x/y/width/height。

| 编号 | 注入位置 | 故障前DI | 故障后DI | 故障后画布 | 结果 |
|---|---|---|---|---|---|
| A1 | executed优先级2000，原生handler返回后、Updater前 | O | O | R | PASS：隔离 |
| B1 | 自有DI恢复前 | O | R | R | PASS：隔离 |
| B2 | DI只写x后 | O | M | R | PASS：隔离 |
| B3 | DI四字段恢复后、画布前 | O | T | R | PASS：隔离 |
| C1 | 派生画布仅写x后 | O | T | M | PASS：隔离 |
| D1 | Undo DI写x后 | T | O | O | PASS：隔离 |
| D2 | Redo DI写x后 | O | T | T | PASS：隔离 |
| D3 Undo | Undo画布派生写x后 | T | O | O | PASS：隔离 |
| D3 Redo | Redo画布派生写x后 | O | T | T | PASS：隔离 |

每例独立会话，direction准备历史后才arm；恰好一次注入，返回success=false且错误包含注入方向/位置。记录注入瞬间节点状态及生命周期、故障前完整状态、隔离前诊断完整状态。D类即使节点数值一致，也不能据此宣称历史恢复成功：例如Redo失败诊断中canUndo与canRedo同时为true。公开布尔值不足以证明内部历史完整，故禁止继续使用并销毁。

A1不是原生handler内部中途抛异常：公开executed足以可靠定位handler已经返回、命令尚未完成的阶段。直接在handler函数体内指定语句注入没有已验证公开入口，该更深覆盖标记BLOCKED，不进行monkey patch或私有替换。

所有病例诊断中元素ID/数量、画布端点、业务sourceRef/targetRef及XML引用与故障前一致；无重复或丢失。XML导出与解析成功，标准DI与XML逐点一致，但B1/B2等XML确实含部分更新，不是可保存的正确结果。九例均阻断五种后续操作，100ms后事件/注入记录未增长。连线故障现场前后路径见各JSON，不以“无拓扑变化”冒充整体回滚。

## 5. 测试与工程检查

- 新增故障测试：9 PASS、0 FAIL、0跳过，退出码0；max-failures=1启用，未触发停止。
- 第一/第二门槛回归：2通过，退出码0。旧测试未修改。
- 严格TypeScript：失败，原有Element Plus/VueUse声明错误，日志failure-typecheck-strict.log；不认定严格检查通过。
- 应用类型检查skipLibCheck：通过；新增文件ESLint：通过。
- Vite构建：通过，有原有chunk体积警告。测试专用动态加载模块未接入正式应用入口。
- 全量旧DI失败测试未重写，不用本轮测试覆盖旧结果。

## 6. 证据索引

failure-result.json：最终故障测试原始结果。
failure-A1/B1/B2/B3/C1/D1/D2/D3-undo/D3-redo.json：各例before、注入瞬间、诊断DI/画布/XML/ID/拓扑、公开历史标志、拒绝结果、事件记录。
failure-summary.json：首次运行摘要，最终逐例JSON和failure-result.json为最终依据。
failure-baseline-regression.json：两道门槛回归。
failure-typecheck-strict.log、failure-typecheck-app.log、failure-lint.log、failure-build.log：检查日志。

## 7. 方案评审结论

1. 公开生命周期和自有边界可以感知本次注入异常；没有误报操作成功。
2. 未证明自动恢复模型、DI或原生历史，未实现事务回滚。
3. 画布派生失败可捕获、采证并销毁隔离；不以画布反写修复DI。
4. Undo/Redo失败后不继续执行历史，因此安全性来自弃用会话，不是历史已恢复。
5. 所有测试可达保存/导出及修改入口封锁通过；未验证真实后端保存或其他插件绕过入口。
6. 未观察到不可控循环、重复注入或隔离后的二次覆盖。公开历史标志显示可能处于部分执行状态，不对私有历史一致性作结论。
7. 第三道门槛仅在允许的会话隔离策略及上述生命周期范围通过；原生handler内部故障BLOCKED。不得宣布方案A整体通过。

建议先评审“故障即销毁并重载”的会话契约以及A1覆盖边界，再决定是否进入完整Adapter设计。复杂组合最终快照、真实重载、异步竞态、未知扩展安全保留、完整规则、严格类型检查仍开放。本轮停止，不自动新增其他命令或正式接入。
