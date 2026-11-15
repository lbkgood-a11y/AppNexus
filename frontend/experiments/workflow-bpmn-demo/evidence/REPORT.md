# 阶段一技术验证报告

日期：2026-11-15（Asia/Shanghai）。结论：独立Demo可构建与运行，阶段一未通过退出门槛，不接入正式模块。

## 依赖与环境

Node 26.13.0；显式pnpm 11.25.0；bpmn-js 18.31.1；Vue 3.5.44；Vite 8.3.5；TypeScript 6.0.3；Element Plus 2.14.7；vue-tsc 3.3.14；Playwright 1.58.2。精确传递依赖见独立pnpm-lock.yaml。安装出现peer warning，尚未全部消除。默认系统pnpm为10.14.0，未修改系统配置。

## 静态验证

- Vite build通过，有chunk体积警告。
- ESLint通过（TS范围，尚未加入Vue SFC lint规则，不宣称全文件审查）。
- 应用代码vue-tsc --skipLibCheck通过。
- 严格vue-tsc失败：Element Plus/VueUse声明涉及Bluetooth、Temporal、GlobalComponents。原始日志typecheck.log保留，不使用any声明覆盖整包。
- 浏览器使用本机Google Chrome真实执行；Playwright自带Chromium启动失败后改用已安装Chrome，未变更业务依赖版本。

## 浏览器结果

{'startTime': '2026-11-14T16:23:17.090Z', 'duration': 4690.062, 'expected': 8, 'skipped': 0, 'unexpected': 1, 'flaky': 0}

| 用例 | 状态 |
|---|---|
| MAP EXT XML DI ID：十类配置与图形往返 | passed |
| CMD：属性、流程、节点、连接、移动撤销重做 | passed |
| SAFE IMPORT：错误输入不覆盖 | passed |
| UNK：实际未知元素解析结果归档 | passed |
| THEME：浅深与窄屏截图 | passed |
| CMD COMPOSITE：业务配置连续组合恢复与节点删除 | passed |
| UNK attributes：未知属性和嵌套元素实测 | passed |
| DI 精确折点、默认流、条件和多实例往返 | failed |
| SAFE 重复ID及取消导入保护 | passed |

原始结果见browser.json，扩展诊断与DI对比见同目录附件，主题截图见light.png/dark.png/mobile.png。测试无跳过。fixture BPMN见附件；测试使用合成配置，不是真实业务目录。

## 八项范围判定

1. 环境兼容：构建和应用类型检查通过，严格依赖声明检查仍有风险。
2. moddle：已知kind与包含中文、集合、空值的payload往返通过；生产类型化嵌套descriptor未完成。
3. XML：已知十类图形往返通过；导入失败保护及重复ID通过。
4. DI：基础节点与连线路径可序列化；非整数折点精确保留失败，属于架构评审阻断。
5. 未知扩展：原生属性、元素及嵌套内容行为已有附件；未知安全保留层、未知执行扩展分类器尚未完成，不能承诺安全可发布。
6. 命令：基础建模与单业务配置命令撤销通过；真正跨多个命令的组合事务、失败回滚、异步导出竞态尚未完成。
7. 主题：浅深和手机截图已生成，暗色滤镜仅证明可见性；System入口、选中/禁用对比及正式主题联动未验收。
8. 映射：十类标准元素和业务kind基本映射通过，ACTION/WAIT仍是ReceiveTask；Flowable descriptor与执行兼容未验证。

## 架构问题及下一步

非整数DI精确保留未满足原验收，不放宽断言、不改权威模型。须先评审是否在Adapter中保存精确DI并恢复，或明确建模内核的量化边界；不能静默采用误差阈值。当前不满足进入阶段二条件。后续需完成规则层、未知扩展安全保留、复杂组合命令及故障测试；当前probe不是生产实现。

工作区保护检查见workspace-integrity.json，基于实施前所有已跟踪文件内容哈希；不含原有未跟踪文件的完整审计。本次新建目标目录前检查不存在，未改正式业务文件或根锁文件。没有执行git reset/clean/restore。

## 实测附件摘要

unknown-extension.json: {
  "warnings": [],
  "preserved": true
}

unknown-details.json: {
  "warnings": [],
  "attribute": true,
  "nested": true,
  "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:devhub=\"urn:devhub:workflow:probe\" id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n  <bpmn:process id=\"Process_1\" isExecutable=\"false\">\n    <bpmn:startEvent id=\"StartEvent_1\" />\n    <bpmn:startEvent id=\"Node_START\">\n      <bpmn:extensionElements>\n        <u:nested xmlns:u=\"urn:probe:unknown\">\n          <u:child value=\"keep\" />\n        </u:nested>\n        <devhub:config kind=\"START\">\n          <devhub:payload>{\"people\":[{\"kind\":\"MEMBER\",\"reference\":\"member_probe\"}],\"completion\":\"ANY\",\"allowSelf\":false,\"capability\":\"probe\",\"required\":[\"姓名\"],\"input\":{\"name\":\"l

di-comparison.json: {
  "same": false,
  "before": "[{\"x\":10.25,\"y\":20.5},{\"x\":100.75,\"y\":20.5},{\"x\":100.75,\"y\":200.125}]",
  "after": "[{\"x\":10.25,\"y\":20.5,\"original\":{\"x\":380,\"y\":21}},{\"x\":100.75,\"y\":20.5},{\"x\":100.75,\"y\":200.125,\"original\":{\"x\":101,\"y\":180}}]",
  "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:devhub=\"urn:devhub:workflow:probe\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n  <bpmn:process id=\"Process_1\" isExecutable=\"false\">\n    <bpmn:startEvent id=\"StartEvent_1\" />\n    <bpmn:startEvent id=\"Node_START\">\n      <bpmn:extensionElements>\n        <devhub:config kind=\"START\">\n          <devhub:payload>{\"people\":[{\