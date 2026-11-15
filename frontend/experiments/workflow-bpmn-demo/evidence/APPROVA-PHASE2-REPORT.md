# ApprovaPlat移植第二阶段实验报告

结论B：部分通过，关键执行中原子性不成立；会话闭锁和完整可编辑Codec尚未验收，不批准正式集成。

## 执行证据与分类

真实Playwright浏览器集成：`npx playwright test tests/approva-phase2.spec.ts --reporter=json`，7个独立测试通过（六故障观测+一个双实例/Flowable用例）；`tests/approva-codec.spec.ts`一个测试通过。多断言不另计测试。原DI/move回归12用例中10通过2原失败保留，见approva-phase2-regression.json。应用类型检查skipLibCheck及新增文件ESLint通过；没有纯单元/后端集成测试、没有Flowable执行证明。

## 实验一：执行中故障

公开eventBus注入六独立用例：第一个updateModdleProperties executed、第二个executed、父preExecuted（全部子命令完成）、父executed（父历史已记）、子reverted、redo子executed。所有异常真实抛出并捕获，before/after配置、canUndo/canRedo、图形坐标、诊断XML和trace见phase2-between/second-executed/all-done/history/undo/redo.json。

这些位置不能证明handler函数体“写字段期间”注入；second-executed为第二handler返回后公开边界，内部中途注入BLOCKED。原机制未自动回滚已成功子修改，属于C部分更新或E历史不确定，不能称命令事务成功。未读取私有栈，canUndo/canRedo不能证明历史数组完整。父历史错误边界确实可公开注入。

测试的五次“拒绝操作”仅对测试局部closed守卫反复调用，不是真实Adapter编辑/保存/导出入口封锁，故不得将7项测试通过解释为可靠会话隔离验收通过。实际Modeler已销毁，但仍需封闭原引用与迟到回调验证。不能依靠清空CommandStack或第二历史栈解决。建议后续在既有SessionManager受控入口中故障即失效/销毁，不引入自动补偿假装原子回滚。

## 实验二：两个Modeler

A配置修改不影响B；B undo不影响A；A销毁后从XML创建新实例历史canUndo=false。PASS限定正常编辑隔离。A故障对B、监听器注册计数/销毁后延迟Promise与事件未覆盖，仍未验证，不能用不同节点或本用例代替。

## 实验三：Codec

新增experiment-codec.ts用过程级codec:Model JSON body保存完整实验WorkflowModel，包含流程字段、十类全部节点字段、people/input/required、matches/terminalActions/copies/withdrawalCapability及连接条件字段，重复编码解码严格相等；未知元素namespace阻断。PASS仅完整JSON镜像。

未实现标准BPMN属性编辑反投影：Modeler修改节点后JSON镜像不会自动同步，encode也未写出受控条件/完整网关执行属性；因此不满足用户要求的完整可编辑WorkflowModel→XML→Modeler→编辑→XML→模型链路，BLOCKED/未完成。该Codec不是正式业务实现，不能充当第二模型权威。未知属性namespace（只检查元素）覆盖也不足，不能宣称所有未知扩展安全阻断。正式模型没有独立输出配置字段，不能自行编造。

## 实验四：Flowable实际值

现有descriptor注册成功：UserTask flowable:assignee与flowable:candidateGroups分别以${wf_actor}/approvers序列化重导，修改${other_actor}/managers后二次往返，值与对象归属符合预期，namespace=http://flowable.org/bpmn。PASS前端Moddle/XML层。DevHub kind/人员/超时仍在devhub namespace，不改ACTION/WAIT ReceiveTask。不启动后端，不能宣称执行兼容。

## 实验五：两个DI失败

本轮实际重跑原断言，两失败仍存在，堆栈见approva-phase2-regression.json。probe完整waypoints比较：导入ImportDockingFix增加original字段，标准x/y未变，是内部对象比较边界；未修改断言。di.spec：BpmnImporter对Bounds Math.round，未编辑XML原DI保留；移动/撤销时BpmnUpdater从取整画布写回，确实影响XML。原失败用例未加载atomic模块，故非本移植引入。专用move/connected-move通过仍只代表限定精度探针，不能宣布通用解决。

## 新增/保护

新增src/experiment-codec.ts、tests/approva-phase2.spec.ts、tests/approva-codec.spec.ts及本轮phase2证据；没有修改任何基线既有文件，依赖/锁文件、正式业务、后端、协议不变。完整审计范围为git ls-files cached+others exclude-standard的基线文件（忽略文件和外部仓库不在范围），结果approva-phase2-integrity.json；不能声称包括被忽略的node_modules/dist。现有所有测试保留，无删除/跳过/弱化。未发现范围外基线变化。

未完成事项明确保留：真实受控会话闭锁、执行失败回滚/历史一致性、双实例故障泄漏、可编辑双向Codec、完整条件和DI、未知属性扩展检测。总体B，不以局部测试PASS掩盖未完成。需要继续针对性补验收，不能提交正式集成评审。
