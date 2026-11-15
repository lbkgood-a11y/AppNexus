# SessionManager 最小实现与保存竞争模拟报告

日期：2026-11-15；状态：独立Demo限定验证通过，正式服务端能力未实现；方案A整体仍未通过。

## 1. 实际文件

新增src/session-types.ts、src/session-resource-registry.ts、src/session-manager.ts、src/mock-document-server.ts、tests/session-manager.spec.ts及本报告/本轮session-*证据。未另建save-request-manager：跨会话请求登记暂由SessionManager统一持有，避免不必要分层。未改正式入口，测试通过Vite动态加载新模块。

## 2. 状态机与准入

Session：CREATED→LOADING→ACTIVE；ACTIVE/LOADING可INVALID；CREATED/LOADING/ACTIVE/INVALID可DISPOSING→DISPOSED。旧Session DISPOSED为终态。RELOADING是Manager控制状态，不让旧对象从DISPOSED复活。转换显式校验，字段内部私有。保存状态独立IDLE/PENDING/SUCCEEDED/FAILED/UNKNOWN，ACTIVE可同时PENDING，但冻结命令。

新会话crypto.randomUUID sessionId，Manager generation单调增加，Modeler/DOM重新创建。Adapter引用当前为最小已提交XML缓存，不是完整DI精度Adapter。加载真实Modeler；新增/删改/连线/发布/预演等统一invoke只模拟准入，不能宣称这些业务实现已完成。十二种入口逐项拒绝INVALID、DISPOSING、DISPOSED。内部Session协作方法不是对不可信插件的沙箱；正式封装及所有原生输入通道仍待验证。

## 3. 资源与异步

SessionResources按所属sessionId登记，逆序一次清理，单项失败继续，结果保留PASS/FAIL。真实Modeler.destroy、DOM移除、缓存释放；测试登记外部事件、ResizeObserver、定时器、模拟Vue退订回调并检查次数。不是完整Vue应用组件卸载证明。重复dispose不重复副作用，关闭后注册拒绝。清理失败阻止reload误激活新会话。

普通Deferred Promise不会被destroy取消，结束时检查sessionId/generation/ACTIVE，迟到记录IGNORED。事件闭包绑定旧身份，不能调用新会话操作。旧响应只能结算请求登记，不更新已销毁Session或新Session revision。已确认终态后迟到网络拒绝不把登记降回UNKNOWN。未实现通用AbortController，也不依赖它撤销服务端操作。

## 4. Mock服务端契约

Mock固定可信认证租户；请求tenant/definition必须匹配授权上下文。文档只返回结构化副本，提交同步原子更新revision和正文。requestId相同且完整信封一致复用响应；不同内容冲突。相同revision两个写只有一个成功。

可信server.grant产生递增token，并将已接收旧PENDING请求永久标记FAILED/FENCED；commit再次检查token。未来才到达的旧token请求也不能提交。sessionId/generation只作客户端隔离，不授予权限。客户端不能指定更高token获得授权。

query只回答已接收requestId状态；PENDING不构成屏障，不存在请求抛UNKNOWN_REQUEST_NOT_TERMINAL，不能猜测未提交。终态在Mock内不可逆。commit与response delivery分离，Deferred传输支持延迟、丢失；不使用sleep驱动竞态。grant是Mock服务端契约，不是正式后端已有接口，也不是客户端generation能实现的保护。

## 5. 六类保存竞争

| 场景 | 结果 | 证据 |
|---|---|---|
| A 请求处理中 | PASS | S09：读到revision0仍阻塞reload；终态后加载revision1 |
| B 成功丢响应 | PASS | S10：UNKNOWN经query确认SUCCEEDED，加载revision1 |
| C 失败丢响应 | PASS | S11：query确认FAILED，仍加载revision0 |
| D 迟到保存 | PASS（Mock） | S12：B先被PENDING阻塞；server grant终结A，B才激活；迟到commit=FENCED且revision不变 |
| E 重复requestId | PASS | S13：两次commit同receipt，revision仅增加1；同键改内容拒绝 |
| F revision冲突 | PASS | S14：同revision两会话，第二个REVISION_CONFLICT，无覆盖 |

S15服务端已提交但旧响应延迟到新会话后才交付，新会话view不变，旧revision不回写。S16正文加入不同process名称，确认新Modeler加载来源是server committed XML且revision1。服务端主体授权在Mock简化为可信租户，不模拟真实账号/CSRF/网络事务。

## 6. S01–S20最终结果

| 编号 | 结果 | 断言 |
|---|---|---|
| S01 | PASS | ACTIVE、Modeler存在、非法状态跳转拒绝 |
| S02 | PASS | INVALID十二入口拒绝、不能复活 |
| S03 | PASS | INVALID/DISPOSING/DISPOSED入口拒绝，包含undo/redo |
| S04 | PASS | 保存导出及全部模拟入口拒绝 |
| S05 | PASS | Deferred迟到IGNORED，新身份/版本不污染 |
| S06 | PASS | 旧事件不执行新命令 |
| S07 | PASS | Modeler/DOM/监听器/observer/timer/退订清理 |
| S08 | PASS | 重复清理次数不增加，关闭资源注册拒绝 |
| S09 | PASS | ACTIVE+PENDING正交状态、编辑冻结、reload等待终态 |
| S10 | PASS | 成功丢响应确认SUCCEEDED |
| S11 | PASS | 失败丢响应确认FAILED |
| S12 | PASS | Mock服务端栅栏阻止迟到提交 |
| S13 | PASS | 幂等/同键不同内容冲突 |
| S14 | PASS | 乐观锁唯一胜者 |
| S15 | PASS | 旧保存响应不回写新/旧会话版本 |
| S16 | PASS | 最新正文和revision原子匹配并加载 |
| S17 | PASS | 查询失败保持INVALID及写封锁，可受控重试 |
| S18 | PASS | 坏XML初始化失败，新实例DISPOSED，不开放写 |
| S19 | PASS | 清理故障被记录，其余资源继续清理，reload不激活 |
| S20 | PASS | 新旧对象/Modeler隔离；旧事件拒绝；跨租户/定义读取拒绝 |

逐例session-S01.json至session-S20.json包含会话身份、状态、请求信封、requestId、expectedRevision、终态revision及资源结果。测试凭据/租户为合成值，不代表真实授权。

## 7. 工程检查

最终命令：npx playwright test tests/session-manager.spec.ts tests/move-only.spec.ts tests/connected-move.spec.ts tests/failure-injection.spec.ts --max-failures=1 --reporter=json。

PASS：31通过、0失败、0跳过（20新增+1普通move+1关联move+9故障）。原始session-manager-all-result.json；初次session-manager-result.json非最终依据。

PASS：vue-tsc --noEmit --skipLibCheck、npm run lint、npm run build。构建有原有chunk体积警告；lint当前不包含Vue SFC规则。严格vue-tsc仍FAIL：原有VueUse Bluetooth/Temporal、Element Plus GlobalComponents声明，未更改依赖或声明隐藏错误，日志session-manager-strict-typecheck.log。

旧probe/di中原始失败断言保留；不将其结果伪装通过，未纳入三道已通过门槛回归集。

## 8. 工作区保护

session-manager-workspace.json记录开始/结束git status及基线内容校验；基线哈希在/tmp/session-manager-baseline.json。开始记录的所有Demo与两份设计文档内容未变，锁文件和依赖未改变。本轮未写正式模块、后端或六个外部文件。六文件未做本轮内容哈希，不能证明其他任务没有并发变更。未reset/clean、未批量格式化或清理。

## 9. 未解决风险与下一步

BLOCKED（正式环境）：请求终态查询/终结屏障及可信token栅栏尚无正式契约实现证据。Mock通过不代表正式后端已有能力。

OPEN：跨标签/进程、真实HTTP认证和丢包、服务端事务故障、通用importXML销毁竞态、请求受理前迟到网络包的端到端验证、所有Modeler原生输入通道门禁、临时XML隐私/内存、真实Vue卸载、发布请求结算。

当前仅Manager级模拟入口准入与真实Modeler生命周期，非完整Adapter。新Modeler初始导入无几何精度适配，不应认为几何方案A已集成或整体成立。没有事务回滚；故障处理仍采用隔离/销毁。

建议进入正式SessionManager契约设计评审（不是接入实施），优先解决服务器终结/栅栏、能力封装和真实请求测试。继续保留独立Demo；完成报告后停止，不新增其他几何命令或X6。
