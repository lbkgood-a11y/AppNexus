# 租户模块公开入口

`@appnexus/tenant` 保留已有租户资源标识，`@appnexus/tenant/administration` 为平台目录类型和注入 API，`@appnexus/tenant/management` 为组合式业务页面。package.json 严格 JSON 参数说明见 docs/design/租户用户成员组织操作流程.md 第 5 节；vue、vue-i18n 与 element-plus 使用宿主现有实例，不引入并行 UI 体系。
