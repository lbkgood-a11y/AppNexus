# 单据管理前端

平台目录与租户流程绑定复用 BusinessWorkspace、ManagementTable、ManagedDialog、Element Plus 表单，不新增 UI 库。平面类型目录采用标准列表，复杂绑定在统一弹窗中按流程及条件组合编辑。详情契约只读，不动态生成业务表单。

页面操作绑定使用 Element Plus Select，只展示业务代码声明已接入本单据的页面操作。选择后显示只读单据行为、权限与处理接口，再配置提交方式、条件和流程；operationKey 与策略一起保存发布。不能用共享权限码将未接入按钮绑定成虚假的业务入口。

平台单据管理增加“页面操作关联”维护表：按行为选择兼容入口，支持添加、移除及全量版本化保存，独立于租户策略保存。租户目录仅提供平台有效关系，业务页面命令执行前也复核关系，已有历史回调不受页面入口调整影响。布局继续复用统一弹窗内 Element Plus 表格及选择器，不新增组件或视觉体系。

package.json 的 private=true 禁止发布，type=module 使用 ESM；exports 为公开组件和类型入口；build/typecheck 执行 Vue 类型检查，test 执行 Vitest；peerDependencies 使用应用已有运行时。tsconfig 的 target=ES2022、module=ESNext、moduleResolution=Bundler 对齐 Vite，strict=true 开启严格检查，noImplicitAny=false 与现有领域包保持一致以兼容公共表格模板回调推导，lib 包含浏览器 DOM，include 仅编译模块源码。这些是开发配置，不新增运行时环境变量。
