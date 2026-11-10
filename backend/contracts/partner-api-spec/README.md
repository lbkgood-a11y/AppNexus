# API 契约与检查

`src/main/resources/openapi-app.yaml`、`openapi-platform.yaml` 是正式 Controller 分组生成的快照；`openapi-partner-v1.yaml` 是经过公开 DTO 限制、scope、幂等、分页、错误和限流说明补充的外部契约。不能将内部总体分组或框架 OAuth 仓储字段直接发布给合作方。

重新生成第一方与平台快照：先执行 `mvn -pl services/platform-service/application -am test -Dtest=ApiContractTest -Dsurefire.failIfNoSpecifiedTests=false`，检查 `services/platform-service/application/target/contracts/` 中产物和接口面差异，再复制相应文件。产物禁止手工修改；生成不会自动覆盖已发布基线。外部手写契约同步到正式 `/v1` 实现及集成测试。

构建 `contracts/partner-api-spec` 自动执行 `ApiContractChecksTest`：检查 OpenAPI 版本、路径命名、operationId 唯一、安全声明、三接口面边界和敏感字段。`API_BASE_REF` 可选，未设置不比较历史；设置时为已发布或 PR 基准 Git revision，必须能解析，最长 200 个字母数字及 `._/-`，不能以 `-` 开头。CI 自动从 PR 目标提交或 push 前次提交提供；该配置只用于构建，不是运行时授权参数。

兼容门禁采取保守比较：删除已存在节点、改变字段类型/约束、必填或列表等均失败；描述和可选节点新增允许。该检查不能判断业务语义是否变化，安全修复、主版本演进和调用方迁移仍需按《API命名与生命周期管理规范》评审。首次新增文件无历史基准可比较，但未知 revision 不会静默跳过。

当前平台运行路由仍为 `/bff/platform/**`；生产建议平台域名入口映射需由部署完成。不能只因有独立契约就宣称 Host 隔离已验收。契约元数据端点只允许权威平台管理员，普通账号与匿名不可读取。
