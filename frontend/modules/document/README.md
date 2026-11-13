# 单据管理前端

平台目录与租户流程绑定复用 BusinessWorkspace、ManagementTable、ManagedDialog、Element Plus 表单，不新增 UI 库。平面类型目录采用标准列表，复杂绑定在统一弹窗中按流程及条件组合编辑。详情契约只读，不动态生成业务表单。

租户以业务行为选择策略，列表/详情入口共用 canonical operationKey；旧别名由后端读取时规范化。工作台是条件、服务与流程/打印场景绑定的唯一维护入口。

平台“已接入操作”从代码自动登记，展示完整页面与 API，提供搜索及每页 10 条分页。关系本身只读；启停通过独立 page-controls 接口，必须填写 1—200 字原因，乐观锁和审计由服务端执行。旧允许列表在启动阶段转换为禁用覆盖，保留遗漏入口的关闭意图，未维护类型自动登记。复用现有表格、分页、输入框及确认组件，无新增组件。

package.json 的 private=true 禁止发布，type=module 使用 ESM；exports 为公开组件和类型入口；build/typecheck 执行 Vue 类型检查，test 执行 Vitest；peerDependencies 使用应用已有运行时。tsconfig 的 target=ES2022、module=ESNext、moduleResolution=Bundler 对齐 Vite，strict=true 开启严格检查，noImplicitAny=false 与现有领域包保持一致以兼容公共表格模板回调推导，lib 包含浏览器 DOM，include 仅编译模块源码。这些是开发配置，不新增运行时环境变量。
