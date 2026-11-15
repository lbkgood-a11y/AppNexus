# ApprovaPlat组合命令与Moddle选择性移植报告

结论B：部分通过，需要补充验证，不能进入正式设计器。

上游固定提交971d229e4b0f9b7e906a014eb803b2a5858a2fb0；当前bpmn-js18.31.1、diagram-js15.29.0、Vue3.5.44、TypeScript6.0.3、Vite8.3.5，依赖未变。移植在独立目录新增文件，不改旧测试。

| 项目 | 预期 | 实际 | 结果 |
|---|---|---|---|
| 组合单次Undo | 两项完整恢复且无额外撤销 | completion/timeout完整恢复，canUndo=false | PASS |
| Redo | 完整后态 | 与首次后态相同 | PASS |
| 连续Undo/Redo | 状态一致 | 两次操作前后恢复 | PASS |
| Undo再编辑 | redo分支清除 | canRedo=false | PASS |
| 节点隔离 | ACTION不受REVIEW修改 | 完整配置相等 | PASS |
| 参数失败 | 不发生部分修改 | retries=-1前置拒绝，配置不变 | PASS（仅前置） |
| 执行中失败 | 不完整状态不可出现 | 尚无故障注入，不能以预校验替代 | BLOCKED/未验证 |
| 业务扩展往返 | 字段不丢 | 10类kind及人员/输入JSON等完整比较 | PASS（实验schema） |
| Flowable扩展 | 引擎字段往返 | descriptor已声明，测试未覆盖实际值 | 未验证 |
| 十类编辑能力 | 所有能力保留 | 本轮只验证配置/类型初始化与往返，不是逐类完整编辑 | 部分 |
| DI回归 | 无非预期变化 | 新用例属性后XML重载DI一致；原回归10通过2失败 | 部分 |

测试分类：真实Playwright新增1用例通过（多断言）；真实浏览器集成Modeler/Moddle。没有独立纯单元测试，也没有后端/Flowable集成测试。应用类型检查skipLibCheck通过，新增TS范围ESLint通过。原DI失败断言完整保留：probe.spec的完整内部waypoint对象比较、di.spec的Bounds移动撤销精度，均未修补或弱化。原move-only/connected-move回归包含在10通过内。测试结果approva-port-result.json、approva-port.json、approva-regression.json。

采用现有evidence/connected-initial.bpmn含真实Demo非整数Bounds和连线作为底图，再加入现有10类映射及sample业务字段；不是正式生产流程样本。尚未完成WorkflowModel→XML独立codec、未知扩展不丢失或阻断、默认流/完整模型字段往返、Flowable属性、逐类创建连接删除及执行中组合异常。因此不能宣称完成用户全部验收项。

复用价值：直接采用上游preExecute调用updateModdleProperties组合历史机制，核心思想对应上游约20余行；最小TypeScript模块另有注册/类型/上下文记录，未借用其RuoYi API/Store。devhub descriptor、映射、实验Adapter、测试均新写，Flowable descriptor只选择少量引擎属性声明方式，不能称复制完整上游扩展体系。时间节省未记录对照工时，不能提供百分比或工期节省。机制省去自行设计第二历史栈，但异常原子性仍需独立方案。

PropertiesPanel有参考价值，但当前实验不支持继续整包移植：其API目录、身份、表单、连接器、RuoYi组件需重写，且我们TypeScript/schema边界仍未完成。正式采用组合机制前需补失败闭锁/会话隔离及结构化schema；当前全部业务变更经commandStack，无新并行Vue业务状态。

新增：atomicPropertiesModule.ts、businessMapping.ts、approva-port.ts、moddle两JSON、approva-port.html、tests/approva-port.spec.ts、README-APPROVA.md、MIT许可及本轮证据。无正式后端、协议、业务代码写入。工作区开始基线/tmp/approva-port-baseline.json，只覆盖Demo既有文件；范围外前后全哈希未建立，不能声称完整范围外并发变动审计。所有本轮写命令限于Demo目录，未清理或覆盖旧实验。

下一步仅建议补齐上述未验证项后重评，不批准正式移植。当前可运行入口只用于技术演示。
