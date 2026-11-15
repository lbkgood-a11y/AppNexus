# ApprovaPlat移植第三阶段：SessionManager故障闭锁与生命周期报告

日期：2026-11-15；总体结论C（当前封装机制不能证明所有旧Modeler入口失效；暂停移植路线，先重新评估对象能力边界）。本实验不构成正式集成批准。

## 1. 实际修改

新增：src/fault-closed-session.ts、src/fault-session-entry.ts、fault-session.html、tests/approva-phase3.spec.ts、evidence/approva-phase3-result.json、approva-phase3-all-tests.json、approva-phase3-types.log、approva-phase3-lint.log、approva-phase3-build.log、CODEC-FIELD-MAP.md、本报告及phase3-*附件。

未改phase2报告、phase2代码及原断言。依赖/锁、正式Workflow Designer、WorkflowModel、后端、数据库、保存协议均未改。

## 2. 实验A：故障闭锁

独立Session状态LOADING→ACTIVE→FAILED→DESTROYED；非法入口只经封装方法，FAILED销毁Modeler和DOM。注入六个真实公开生命周期事件：首个子属性updateModdleProperties.executed、第二个子命令executed、父preExecuted、父executed历史记录边界、Undo子命令reverted、Redo子命令executed。Playwright独立6例全部PASS：注入被捕获，状态变为终态，封装edit/undo/redo/export/save/publish/preview拒绝，宿主移除。全量测试中的测试名：Session失效闭锁 child1/child2/parent-finished/history-recorded/undo-middle/redo-middle。

这证明受控Facade遇到异常后会失效，不证明CommandStack事务回滚。旧XML/Moddle可能留下部分状态；报告保留导出前状态，系统不称其一致，不允许经Facade保存。Undo/Redo异常同样失效。

## 3. 核心阻断：裸引用仍可操作

双Modeler用例创建真实A/B；A故障后其封装状态DESTROYED、hostConnected=false、modelerAvailable=false，B仍可编辑、undo/导出，迟到Promise返回applied=false，注册事件销毁后不再触发。此部分通过。

但测试在故障前保留rawA Modeler引用，destroy后调用rawA.elementRegistry的业务对象，再通过公开modeling.updateProperties把Port_REVIEW名称改成STALE_AFTER_DESTROY，并成功调用saveXML。这是真实观测：销毁Modeler会清理容器/内部资源，但不撤销JavaScript已泄漏对象引用的能力。因此“旧实例任意引用无法修改/导出”失败。被保护的是SessionFacade入口，不是bpmn-js对象本身。

真实产品必须确保Modeler/CommandStack/Moddle/Modeling服务对象不泄漏给扩展、组件、异步回调或全局变量；若插件已持引用，generation无法拦住直接调用。当前实验对象存在rawForAudit，仅测试用途，却明确暴露该失败模式。不能以Modeler.destroy或封装状态代替能力隔离。该问题足以阻止“可靠会话闭锁”验收，状态判BLOCKED。

## 4. 实验二：两个Modeler

A配置修改不影响B；B undo不影响A；A销毁后从XML创建新实例历史canUndo=false。PASS限定正常编辑隔离。旧Promise通过runAsync围栏后记录IGNORED；旧EventBus自有listener经dispose注销，销毁后触发计数不增加。此结果只覆盖注册在Session资源作用域的订阅。

限制：只有通过runAsync包装的任务受保护；任意外部Promise/callback若直接捕获Modeler仍能修改。不能声称所有异步任务自动登记。未覆盖Vue卸载、浏览器多标签及第三方插件留存引用。

## 5. 实验三：Codec字段审计

CODEC-FIELD-MAP.md按正式WorkflowModel.java、前端index.ts和WorkflowCompiler.java列出字段映射。实验Codec仅把完整WorkflowModel JSON放入流程级扩展镜像，用于证明不丢JSON字段；不是真正逐节点/连线的双向BPMN业务Codec。它没有把gateway条件/default、节点业务字段反投影到标准建模操作，未知元素namespace阻断仅覆盖元素，不覆盖所有属性/引用安全。因此JSON镜像往返PASS，双向可编辑Codec BLOCKED；没有编造正式字段，不修改正式模型。

## 6. 实验四：Flowable扩展与DI

阶段2的真实属性往返证明限定于前端moddle/XML：assignee/candidateGroups分别以${wf_actor}/approvers序列化重导，修改${other_actor}/managers后二次往返，值与对象归属符合预期，namespace=http://flowable.org/bpmn。PASS前端XML层。未启动后端，不能宣称执行兼容。

全量回归原有两个失败仍在：
- `DI分层：折点往返及Bounds编辑撤销`：Bounds移动撤销回到取整值530/141/101/80，而预期原值530.25/140.5/100.75/80.125。
- `DI 精确折点、默认流、条件和多实例往返`：保留的waypoint断言失败。

前者已确认bpmn-js导入画布Math.round、编辑后同步DI导致精度损失；后者不能仅凭本轮复述根因，原失败附件继续为准。未修改断言。专用move/connected-move通过仅代表限定精度探针，不能宣布通用解决。

## 7. 测试命令和结果

第三阶段：`npx playwright test tests/approva-phase3.spec.ts --reporter=json`，7测试全通过（6故障+1双实例/迟到回调），证据approva-phase3-result.json与phase3-*。

全量：`npm test -- --reporter=json`，55预期、53通过、2失败、0跳过；approva-phase3-all-tests.json。失败是上述原有DI问题，未弱化。

`npx vue-tsc --noEmit --skipLibCheck`通过；新增文件ESLint通过；`npm run build`通过但有chunk体积警告。三类静态检查不是额外浏览器测试。

## 8. 逐项验收

| 项目 | 结果 | 说明 |
|---|---|---|
| SessionManager受控入口失效 | PASS | 7项受控入口在Facade层封锁 |
| 原子自动回滚 | BLOCKED | 未实现；部分Moddle改动可残留 |
| 旧裸Modeler引用失效 | FAIL | destroy后公开modeling仍能改旧对象并saveXML |
| 迟到异步回调 | PARTIAL | 仅经runAsync围栏者隔离 |
| 监听器销毁 | PASS（实验订阅） | 自有disposer注销通过，非全插件证明 |
| 双Modeler正常隔离 | PASS | B数据/XML/历史独立 |
| WorkflowModel双向Codec | BLOCKED | 当前仅JSON镜像，不是真正可编辑映射 |
| Flowable moddle属性 | PARTIAL | 前端XML层通过，后端执行未测 |
| DI精度 | FAIL（原回归） | 2个既有失败保留 |
| 工作区保护 | PASS（限定范围） | 见下节 |

## 9. 工作区完整性范围

基线/tmp/approva-phase3-baseline.json：全仓Git tracked+untracked+nonignored快照中，实验目录147个文件哈希；git status开始274项。结束比较这147项全部不变，包含第一、二阶段代码、测试与证据。只验证该Demo目录文件哈希，没有计算全仓及ignored node_modules/dist哈希；不声称全工作区完整内容审计。Git状态项目数可能因本轮新增而增加。未触碰范围外文件；没有删除、覆盖、重命名或格式化既有文件，无测试被删除/跳过/弱化。

## 10. 最终判断与后续边界

会话Facade可以对受控调用故障后封锁，但bpmn-js原始对象引用仍具写/导出能力；因此不能证明设计器所有入口安全失效。完整Codec和DI精度仍未闭合。总评C：现有移植路线暂不进入正式集成评审；需重新评估对象能力边界，或改变集成模式以避免裸能力引用，再单独验证。此结论不自动要求更换图引擎。

本轮仅独立Demo实验，没有接入正式模块。停止，不自动开始下一阶段。
