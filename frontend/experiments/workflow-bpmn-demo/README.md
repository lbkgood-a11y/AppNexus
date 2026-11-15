# DevHub bpmn-js 独立技术探针

状态：阶段一验证未全部通过；见evidence/REPORT.md。此Demo不调用正式API，不代表业务设计器。

## 运行

在本目录执行：

```sh
npx --yes pnpm@11.25.0 --ignore-workspace install --frozen-lockfile
npm run dev -- --port 5179 --strictPort
```

打开 http://127.0.0.1:5179 。拖动、连接与删除使用bpmn-js原生工具；浅深按钮只控制本地html.dark。技术API通过浏览器window.probe提供。测试fixture为合成数据。

```sh
npm run typecheck
npm run lint
npm run build
npm test -- --reporter=json
npx vue-tsc --noEmit --skipLibCheck
```

浏览器测试当前使用本机Google Chrome（channel chrome），需要预装Chrome；默认Chromium下载曾出现启动失败，未以失败结果冒充测试成功。最后一条命令仅检查应用代码，严格typecheck第三方声明仍失败。浏览器测试保留DI失败断言，预期当前非零退出。

## 参数与限制

| 参数 | 中文说明 |
|---|---|
| host | 本机127.0.0.1，无公网服务意图 |
| 测试端口 | 5179，无业务端口联动；占用时拒绝启动，不复用未知服务 |
| XML上限 | 1000000字符，探针临时边界；不是生产字节限制 |
| packageManager | pnpm 11.25.0；必须ignore-workspace以免影响正式workspace |
| schema namespace | urn:devhub:workflow:probe，仅实验，不是生产协议 |
| devhub:Config.kind | 现有10类节点标识；不是执行器标识 |
| devhub:Config.payload | 测试业务配置JSON文本；不承诺生产schema已确定 |

## 组件理由与边界

普通表单无法承载BPMN建模生命周期，因此独立App组合Modeler与Element Plus。当前暗色采用滤镜技术探针，不能视为最终产品配色。动态服务类型仅在实验边界使用；正式Adapter必须收窄类型。未知扩展原生行为记录不等于已经实现安全保留适配。默认palette未限制生产节点，本Demo不能用于发布。完整组合事务、异常回滚和规则适配仍待验证。
