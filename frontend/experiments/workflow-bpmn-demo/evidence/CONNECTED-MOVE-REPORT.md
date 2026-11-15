# 第二道门槛：单关联SequenceFlow移动

日期：2026-11-15。结论：本测试场景通过，等待架构评审，不自动扩大范围。方案A整体成立尚未证明。

## 隔离与运行

新增connected-move.html、src/connected-move-probe.ts、tests/connected-move.spec.ts及本次证据。第一道move-probe.ts、move-only.spec.ts及原始失败断言不变。未改正式模块、后端、依赖源码或六个外部变动文件，未reset/clean。

运行：`npx playwright test tests/connected-move.spec.ts --reporter=json`。最终1通过、0失败、0跳过，退出码0。应用类型检查（skipLibCheck）及本次文件ESLint退出码0；严格类型检查仍开放。

## 测试边界

一个被移动StartEvent、一条出向SequenceFlow；另一端为固定UserTask，未添加标签、子节点。delta=(20,10)，保留原生layout，关闭子节点递归。先导入非整数DI并建立空历史，再执行一次公开modeling.moveShape。不是所有节点类型、入向连接或复杂路由的通用结论。

独立探针复制第一道最小机制，只放开到恰好一条连接并增加公开事件观测。不存在连线精度补偿命令；关联路径由原生connection.layout产生并撤销/重做。context.delta作为移动意图；命令上下文保存节点before/after，恢复DI再派生画布。未读取私有栈状态或修改原生实现。

## Bounds与XML结果

| 阶段 | x | y | width | height |
|---|---|---|---|---|
| 初始 | 530.25 | 140.5 | 100.75 | 80.125 |
| Execute | 550.25 | 150.5 | 100.75 | 80.125 |
| Undo | 530.25 | 140.5 | 100.75 | 80.125 |
| Redo | 550.25 | 150.5 | 100.75 | 80.125 |

Execute/Undo/Redo的节点DI、画布与XML重新解析结果严格一致。

## 连线路径与拓扑

初始/Undo精确路径：[(631,180.5625),(700.25,180.5625),(700.25,180),(800,180)]。

Execute/Redo精确路径：[(651,191),(726,191),(726,180),(800,180)]。

执行后的整数路径是原生路由器重新计算结果，不能宣称保留原非整数路径的形状。测试显式锁定前后路径，逐点比较canvas/DI/XML；Undo必须精确恢复原始非整数路径，Redo必须精确恢复后态。所有阶段的画布端点及BPMN sourceRef/targetRef保持同一对稳定ID。仅验证引用拓扑及确定路径，未验收端点几何与精确节点边界的像素级贴合。

## 生命周期与一次撤销

首次：shape.move execute → 原生DI同步 → 精确节点恢复 → postExecute → connection.layout preExecute/executed → postExecuted → connection.changed/shape.changed → elements.changed → commandStack.changed。

Undo：connection.layout reverted → shape.move revert/reverted → 精确before恢复 → changed事件。

Redo：shape.move execute/executed → 精确after恢复 → connection.layout executed → changed事件。无pre/post重跑。

导入后canUndo=false；执行后一次undo即恢复节点与路径，并再次canUndo=false、canRedo=true，证明本场景一次用户移动对应一次可预期撤销。

## 二次同步与循环观测

记录shape.changed、connection.changed、elements.changed及commandStack.changed。每轮Move命令恢复次数有限且符合预期：restore after两次（execute和redo），restore before一次。execute与redo后各等待100ms再次读取全部DI/画布/XML/历史标志，精确等于刚完成状态。本场景未发现派生画布导致新的同步覆盖或事件循环；有限时间观测不能证明所有未来插件均无此风险。

## 证据

connected-move-result.json：Playwright结果。
connected-move.json：阶段快照、原生同步及关联布局事件顺序。
connected-initial/executed/undone/redone.bpmn：四阶段XML。
connected-move-typecheck.log、connected-move-lint.log：静态检查。

## 未关闭事项

复杂组合最终快照、多连线、入向与不同拓扑、标签、子节点、Resize、Reconnect、异常回滚、异步保存竞态、未知扩展安全保留、完整规则、严格类型检查均未验证。当前仅节点历史快照，未实现统一多元素事务快照或完整精度适配层。两道最小门槛结果供架构评审，不据此认定方案A整体通过。
