# 组织模块公开入口

`@appnexus/master-data/organization` 暴露组织资源标识、请求端口和展示类型；`@appnexus/master-data/organization-management` 暴露组合式领域页面。应用壳注入 HTTP/CSRF 和错误文案，不向领域页面暴露模板内部模块。

package.json 为严格 JSON，参数在此说明：`exports[.]` 为领域类型入口，`exports[./management]` 为 Vue 页面入口；`peerDependencies.vue`、`vue-i18n` 和 `element-plus` 要求由宿主提供现有 Vue 3、i18n 11 和 Element Plus 2 实例，避免重复框架实例和并行 UI 体系。版本范围与 admin 当前依赖一致，不引入新 UI 库。

## 构建说明

本模块导出组织及人员 Vue 页面，构建和类型检查使用与其他 Vue 业务模块一致的 `vue-tsc --noEmit --skipLibCheck`。检查自有源码，第三方声明采用仓库现有 Vue 模块策略；不关闭空值检查。

严格 JSON 参数说明如下，修改时同步维护本文及锁文件。

| 配置 | 用途与生效范围 |
|---|---|
| scripts.build / scripts.typecheck | 必填，使用 Vue 感知的类型检查器解析源码及跨工作区 `.vue` 导入，无构建输出 |
| devDependencies.vue-tsc | 开发期类型检查器，范围 `^3.1.4` 与现有业务模块一致，精确版本由锁文件固定 |
| devDependencies.vite | 开发期提供 `vite/client` 的 CSS、资源类型，范围 `^8.0.3` 与 admin 一致；不新增运行时服务 |
| compilerOptions.target / module / moduleResolution | 分别为 ES2022、ESNext、Bundler，保持现有打包器解析语义 |
| compilerOptions.strict | true，保留严格空值及其他严格类型检查 |
| compilerOptions.noImplicitAny | false，与现有 Vue 业务模块一致，允许模板组件事件缺少推导类型；不改变运行逻辑 |
| compilerOptions.types | `vite/client`，为样式及资源导入提供类型 |
| include | `src/**/*.ts`、`src/**/*.vue`，覆盖类型入口与真实页面源码 |
