# 普通 shape.move 精度保真最小验证

日期：2026-11-15。结论：本范围通过；不代表方案A完整实现，不进入关联、组合或异常验证。

## 运行与边界

`npx playwright test tests/move-only.spec.ts --reporter=json`：1通过、0失败、0跳过，退出码0，真实Google Chrome。应用类型检查（skipLibCheck）和本次文件ESLint通过，日志move-typecheck.log、move-lint.log。严格第三方声明检查仍开放。

入口move.html；实现src/move-probe.ts；测试tests/move-only.spec.ts。依赖注入additionalModules注册CommandInterceptor。独立Demo显式声明diagram-js 15.29.0直接依赖，与原传递版本一致，未升级版本；正式依赖文件未修改。

目标为无连线、无子节点的普通StartEvent形状。通过公开modeling.moveShape提交delta={x:20,y:10}，hints关闭layout/recurse，本场景不产生关联布局。不是拖拽、吸附或其他节点类型的通用完成声明。

## 机制

移动意图来自公开命令事件context.delta。execute事件优先级1500首次记录冻结before和after到命令context.movePrecision；redo复用已有历史，不覆盖before。after只改变x/y，width/height来自原DI。快照仅作不可变撤销历史，不提供独立布局入口。

executed/reverted优先级750观测原生BpmnUpdater完成后的DI；优先级500恢复目标DI，再从该DI派生画布四字段。未执行新命令，未读取私有栈，未改依赖源码或替换方法。优先级依赖本版本原生同步默认1000，升级须回归验证。

## 实测生命周期

首次：preExecute → preExecuted → execute/捕获意图 → 原生handler → executed(2000，DI尚为before) → 原生DI同步 → executed(750，DI为550/151/101/80) → restore after(500) → postExecute → postExecuted。

Undo：revert → 原生handler.revert → reverted → 原生DI同步 → restore before。

Redo：execute（不运行pre/post） → 原生handler → executed → 原生同步 → restore after。复用首次精确after；完整记录见move-only.json.trace。

观测监听器必须返回void；原型调试时发现返回trace.push的数字会影响EventBus传播，已修正为显式void，最终证据验证原生Updater实际运行，不能把阻止原生同步误当成功。

## 精确结果

| 状态 | x | y | width | height |
|---|---|---|---|---|
| 初始DI/XML | 530.25 | 140.5 | 100.75 | 80.125 |
| Execute DI/画布/XML | 550.25 | 150.5 | 100.75 | 80.125 |
| Undo DI/画布/XML | 530.25 | 140.5 | 100.75 | 80.125 |
| Redo DI/画布/XML | 550.25 | 150.5 | 100.75 | 80.125 |

导出XML通过DOMParser重新解析Bounds，以严格相等比较四个数值。无容差、截断或隐藏差异。初始画布仍呈原生取整投影530/141/101/80；首次命令收敛后画布从精确DI派生，与DI精确相同。当前仅测数据模型及序列化，不声称截图视觉或后续其他命令已验证。

原始JSON：move-only-result.json；分层快照：move-only.json；四份XML：move-initial.bpmn、move-executed.bpmn、move-undone.bpmn、move-redone.bpmn。

## 未关闭事项

关联连线、组合/嵌套命令、异常回滚、异步竞态、resize/reconnect/waypoint、未知扩展安全保留、完整规则、严格类型检查均未验证。未修改旧probe.spec.ts、di.spec.ts断言或既有报告。无正式模块、后端、已记录六文件修改，无reset/clean操作。等待架构评审后再扩大范围。
