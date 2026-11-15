# DevHub Workflow Designer 阶段一：bpmn-js 技术验证计划

日期：2026-11-15；状态：待确认，尚未创建Demo或安装依赖。依据[架构修订方案](DevHub Workflow Designer技术改造设计方案.md)。

## 1. 目标与隔离范围

验证当前Vue/Vite/TypeScript环境与bpmn-js的兼容性，以及受控XML、DI、扩展、命令栈、主题和现有10类映射。Demo不能调用正式保存、发布或执行接口，不修改正式工作流组件、数据库、迁移、路由或全局主题。静态fixture仅用于技术测试，不冒充真实业务能力。

拟建目录：frontend/experiments/workflow-bpmn-demo。该目录不匹配当前apps/*、modules/*、infra/* workspace glob；保持独立package和锁文件，不改根workspace、正式package或锁文件。实现时先验证pnpm在该目录的独立安装方式；如无法隔离则停止该安装路径并评审，不擅自改根workspace。node_modules为可再生依赖，不提交。

## 2. 依赖选择

2026-11-15通过npm registry元数据只读查询：bpmn-js最新返回18.31.1；其依赖声明diagram-js ^15.28.0、bpmn-moddle ^10.3.1，engines.node为*。这不是当前工程兼容性验证结果。来源：https://registry.npmjs.org/bpmn-js/latest 。

候选直接依赖固定bpmn-js 18.31.1，避免实验期间漂移；不单独强制覆盖其diagram-js传递依赖。若fixture测试需要直接导入bpmn-moddle，则固定与选定树一致的版本，先检查重复实例。Flowable moddle descriptor尚未在当前工程发现，优先编写覆盖现有固定属性的最小测试descriptor，不能声称覆盖全部Flowable扩展。

Vue、Vite、TypeScript、Element Plus、vue-tsc、Vitest优先使用当前锁文件对应实际解析版本并精确锁定，当前声明分别为^3.5.31、^8.0.3、^6.0.2、^2.13.6、^3.1.4、^4.0.0；实施前核对，不直接升级。浏览器测试工具优先检查现有可用能力；需新增Playwright等工具时仅加到Demo。bpmn-js声明文件与TS6兼容性待验证，不用全局any或自编整包声明掩盖错误。

环境遵守根Node >=24 <27及pnpm 11.25.0。独立package严格JSON参数说明写同目录README中文表；端口、URL、文件容量等系统参数在创建时注明用途、单位、默认值及环境。Demo仅本地访问，端口待检查空闲后确定。

## 3. 拟创建文件

| 文件/目录 | 职责 |
|---|---|
| package.json、独立锁文件 | 隔离依赖及dev/typecheck/lint/test/build脚本 |
| README.md | 运行、依赖决策、中文配置表、限制和复现说明 |
| index.html、vite.config.ts、tsconfig.json、eslint.config.* | 独立Vue/Vite/TS、lint配置 |
| src/main.ts、App.vue | 测试控制台与Vue生命周期 |
| src/components/BpmnProbeCanvas.vue | Modeler装配、销毁、尺寸与只读事件 |
| src/adapter/ProbeDesignerAdapter.ts | 唯一写入口及只读业务投影 |
| src/bpmn/devhub-moddle.json、flowable-probe-moddle.json | 已支持测试扩展描述；配套中文字段文档 |
| src/bpmn/commands/UpdateBusinessPropertiesHandler.ts | 业务属性execute/revert |
| src/bpmn/commands/ConfigureWorkflowHandler.ts | 流程和节点组合命令 |
| src/bpmn/rules/ProbeRules.ts | 交互规则适配，不代替后端校验 |
| src/document/codec.ts、ids.ts、extensionClassifier.ts | 语义比较、稳定ID、扩展分类验证 |
| src/styles/probe.css | 复用Element Plus语义变量的画布适配 |
| fixtures/*.bpmn、fixtures/*.json | 10类节点、DI、扩展及错误输入 |
| tests/unit/*、tests/browser/* | 纯转换与真实浏览器Modeler测试 |
| evidence/README.md | 命令、版本、结果、截图索引及待验证项 |

独立Demo只接收现有主题入口产生的html.dark/语义变量，不新建主题持久化或修改正式ThemeToggle；测试控制台可显式切换class并模拟System，正式全局入口联动待集成阶段验证。所有新公开接口及关键命令采用中文TSDoc；新增组件理由记录为建模专项技术探针，无法由普通表单替代。

## 4. 测试矩阵

| 编号 | 用例 | 验收标准 |
|---|---|---|
| ENV-01 | 干净独立安装、vue-tsc、ESLint、Vite build | 零错误；实际版本树、命令与退出码归档；正式锁文件无改动 |
| ENV-02 | Vue重复挂载/卸载、窗口resize | 无重复画布、残留监听、控制台异常 |
| EXT-01 | scalar、嵌套对象、集合、中文、空值及缺省业务配置 | 导出再导入后业务语义一致；缺省规则明确 |
| XML-01 | 导入→导出→再导入 | 元素类型、ID、端点、条件和默认流一致，不按字符串空白比较 |
| DI-01 | 非整数坐标、尺寸、标签、折线waypoint | 未编辑往返数值一致；XML数值表示允许规范化；不静默适配或重排 |
| DI-02 | 移动节点及重连后撤销/重做 | DI、连线、业务投影和面板回到对应状态 |
| UNK-01 | 未知namespace属性、extensionElements及嵌套元素 | 记录解析警告和实际丢失情况，不能把未知丢失视为成功 |
| UNK-02 | 安全扩展只读保留原型、未知引用、所属节点删除 | 保留受支持片段；不可证明引用安全时阻断，不静默删除 |
| UNK-03 | 未知listener、delegate、表达式 | 分类为不可发布；不执行、不部署 |
| CMD-01 | 节点增删移动、连线增删、名称与业务属性修改 | 仅一套command stack，每个动作可undo/redo |
| CMD-02 | 流程属性、分支条件、复杂节点组合配置 | 单次撤销恢复全部配置；XML/JSON只读投影/面板一致 |
| CMD-03 | 命令失败、异步导出期间继续编辑 | 不留部分修改；旧导出不可覆盖新编辑 |
| ID-01 | 排序、删除撤销、克隆、导出重导入 | 原节点连线ID不变；克隆新ID；不以数组位置认对象 |
| MAP-01 | START/END/REVIEW/HANDLE/ACTION/WAIT/CHOICE/MERGE/FORK/JOIN | 与正式方案映射一致；ACTION/WAIT始终ReceiveTask |
| MAP-02 | 多实例、join、受控条件及默认流镜像 | 字段可完整往返；缺少kind的歧义不得静默猜测 |
| RULE-01 | 自连、重复、错误端点、循环及不完整并行 | 前端给出稳定诊断；明确后端仍是最终权威 |
| SAFE-01 | 畸形、重复ID、未知引用、DTD、外部实体、超限 | 拒绝且当前会话不变；无外部资源请求；超限阈值实测后固化 |
| IMPORT-01 | 导入失败、取消、隔离预览 | 原图和未保存状态不变；仅确认后替换候选会话 |
| THEME-01 | light/dark/System模拟、选中、连接、禁用 | 文字和连线可辨；不硬编码独立主题；截图归档 |

Modeler与DI/命令测试必须在真实浏览器运行，不以DOM模拟器通过代替。检查1440×900、1024×768、390×844基础布局、键盘焦点与面板可达性。阶段一不宣称完成产品级响应式验收。

## 5. 执行步骤与退出门槛

1. 用户确认计划后记录工作区基线和文件哈希，核对本地运行环境及锁版本。
2. 仅在隔离目录创建文件、安装固定依赖；确认正式依赖文件未变。
3. 先完成最小导入导出与10类fixture，再扩展DI、moddle及命令测试。
4. 对未知扩展记录“原生保留/需适配保留/不可安全保留”三类证据，决定正式契约边界。
5. 运行类型、lint、单元、浏览器及构建，保存完整可复现结果。
6. 输出验证报告，明确通过、失败、未验证、修订建议；再评审阶段二，不直接接入正式设计器。

进入阶段二条件：ENV、已知扩展、XML、DI、稳定ID、全部修改命令及10类映射通过；未知安全扩展具备可证明的保留范围，未知执行扩展阻断；所有失败均有决策，不以跳过断言获得通过。前后正式目录差异检查证明未修改业务代码。Flowable真实部署执行不在此独立前端Demo验收内，必须在后续真实MySQL/Flowable阶段单独验证。

## 6. 风险与待验证

- bpmn-js 18.31.1虽然registry可用，但与TS6、当前Vite的兼容尚未证明；若失败先记录原因再评估邻近版本，不升级正式栈。
- moddle未知扩展可能告警、丢失或无法重序列化；不能承诺任意XML无损编辑。
- 手工保留片段涉及namespace、ID引用及安全边界，失败时收窄可编辑输入范围。
- 组合命令和异步导出可能产生部分回滚或竞态，须故障测试。
- 当前业务条件表达式、多实例和Flowable扩展在前端可序列化不等于引擎可执行。
- 当前依赖实际安装状态、浏览器测试能力、独立pnpm安装隔离方式待验证。
- 不修改、重置或清理现有未提交成果；若目标目录已存在则先检查，不覆盖。
