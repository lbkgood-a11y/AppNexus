# 正式 WorkflowModel 与候选 XML 映射审计（只读）

依据 frontend/modules/workflow/src/index.ts、WorkflowModel.java、WorkflowCompiler.java。实验 XML 使用 bpmn-js 与 DevHub 扩展，不是正式 Codec。

| 正式字段 | BPMN/XML表达 | XML→模型 | 模型→XML | 状态 |
|---|---|---|---|---|
| name | process.name | 可读 | 可写 | 可映射 |
| objectType/purpose/mode/entryCapability | DevHub 流程扩展 | 仅实验JSON镜像 | 仅镜像 | 双向BLOCKED |
| matches/terminalActions/copies/withdrawalCapability | DevHub流程扩展 | 仅镜像 | 仅镜像 | 双向BLOCKED |
| node id/name/kind | BPMN id/name + devhub:kind | 前两项可读 | 标准元素映射 | Kind编辑未完成 |
| people/completion/allowSelf/capability | DevHub扩展JSON字段 | 可解析JSON文本 | 可写JSON文本 | 部分实验，不是正式结构化契约 |
| required/input | DevHub扩展JSON文本 | JSON解析 | JSON生成 | 需结构/字段校验 |
| retries/timeoutSeconds/join/separate | DevHub扩展属性 | 可读 | 可写 | 限制回归不完整 |
| position | BPMN DI Bounds | 导入画布有取整行为 | 单独DI恢复机制 | 不复制为第二权威 |
| Edge from/to | SequenceFlow sourceRef/targetRef | 可读 | 可生成 | 部分 |
| Edge稳定id | 正式Edge无id字段 | 无来源 | 实验另设ID | 正式契约未定 |
| Edge field/value/operator/defaultEdge | condition/default + DevHub扩展 | 当前实验镜像未映射 | 未实现受控条件编码 | BLOCKED |

十类Kind映射：START/END事件，REVIEW/HANDLE UserTask，ACTION/WAIT ReceiveTask，CHOICE/MERGE ExclusiveGateway，FORK/JOIN ParallelGateway。Action/Wait继续ReceiveTask。未验证真实执行部署。

未知XML检查仅按元素namespace白名单，未全面校验未知属性、引用、嵌套扩展或namespace安全，不能声称未知扩展完整保真。
