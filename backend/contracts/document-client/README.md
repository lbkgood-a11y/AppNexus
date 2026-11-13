# 业务远程 SDK

Java 21 Maven 坐标：`com.appnexus:document-client:0.1.0-SNAPSHOT`。依赖公开 document-contracts、print-contracts 和 Jackson，不依赖 Spring 或任何业务/专业服务实现。

## 接入

1. 平台在租户的集成客户端管理选择“单据与打印业务集成”，取得专用客户端标识和一次性密钥。客户端固定租户，scope 为 document:resolve 和 print:submit；不会成为人员账号或管理员。
2. 业务服务使用现有 OAuth client_credentials 获取短期 Bearer Token。Token 与密钥由业务宿主管理，不写日志或浏览器存储。SDK 的 Supplier 只在请求时取得 Token，不自行持久化凭据。
3. 业务 API 先完成最终用户权限、单据归属和依据版本检查，再用 SDK resolve 请求固定配置。调用方只能向已登记类型提交其声明的有限非敏感条件字段。
4. PRINT 返回 sceneCode/dataContract，业务服务授权读取打印投影后调用 printResolved；打印服务验证实际场景及数据 Schema，生成 PREVIEW 或 SILENT 输出。预览确认和状态仍使用现有打印 API。

构造示意：`new DocumentClient(URI.create(serviceRoot), tokenSupplier, Duration.ofSeconds(10))`。serviceRoot 必填 HTTPS 根地址，无路径、查询、片段、用户信息；仅 localhost/127.0.0.1 回环允许 HTTP。timeout 必填，正数且最多 120 秒，无默认值；同一调用使用稳定 UUID 请求键。

SDK 不跟随重定向，不自动重试写操作，不替业务执行状态变化。REMOTE_OUTCOME_UNKNOWN 表示响应未知，必须保留原请求键重放，不改键重复出纸。业务服务调用该 SDK 并不获得跨租户权限，普通浏览器会话无法调用服务专用解析端点。

## 公开 HTTP

- `POST /api/documents/integration/resolutions`：Bearer document:resolve；Idempotency-Key 必填 UUID；JSON 包含 typeCode、actionCode、objectId、basisVersion（非负整数）、facts（声明的非敏感字符串标量）。不得传入租户或账号作为可信身份。返回 Documents.Resolution。
- `POST /api/printing/external/outputs`：Bearer print:submit；同一稳定请求键；参数沿用打印公开接口。mode 为 PREVIEW/SILENT；copies 为 1—99，单位份，无默认值；sceneCode 必填，deviceId 实体打印时必需。返回输出请求 ID、jobId 和状态。

当前远程 SDK 支持配置解析与打印创建；不是远程注册 SDK，也不代表远程流程结果回调、业务代理执行或跨服务原子提交已实现。工作流依旧使用 workflow-contracts 的业务接入端口，跨进程的工作流业务提供器认证与恢复不能由 SDK 伪造。
