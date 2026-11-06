# 组织模块公开入口

`@appnexus/organization` 暴露组织资源标识、请求端口和展示类型；`@appnexus/organization/management` 暴露组合式领域页面。应用壳注入 HTTP/CSRF 和错误文案，不向领域页面暴露模板内部模块。

package.json 为严格 JSON，参数在此说明：`exports[.]` 为领域类型入口，`exports[./management]` 为 Vue 页面入口；`peerDependencies.vue`、`vue-i18n` 和 `element-plus` 要求由宿主提供现有 Vue 3、i18n 11 和 Element Plus 2 实例，避免重复框架实例和并行 UI 体系。版本范围与 admin 当前依赖一致，不引入新 UI 库。
