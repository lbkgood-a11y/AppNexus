# 技术 PoC 执行记录

更新日期：2026-11-06；状态：当前阶段已完成，剩余项需真实浏览器、CI 或目标容量环境。

当前自动化结果：后端 37 项测试通过（0 失败/0 错误）；前端 Node 24/26 下类型检查、3 项单测和生产构建通过。

## 环境

| 项目 | 实际环境 | 结论 |
|---|---|---|
| Java | Eclipse Temurin 21.0.11 LTS | 符合基线 |
| Maven | 3.9.15 | 符合基线，已生成 Maven Wrapper |
| Docker | Docker Desktop 29.6.1 | 可用；本地 `mysql:8.4` 已用于容器测试 |
| Node.js | 系统 23.11.0；工作区 24.22.1；临时官方 26.13.0 | Node 24 与 Node 26 LTS 均完成同一锁文件验证 |
| pnpm | 11.25.0 | 通过；原 `npmmirror` 网络错误由项目级官方 registry 配置解决 |

## 已验证

### 框架兼容性

Java 21 下以同一 Maven reactor 成功编译并测试：

- Spring Boot 4.1.2 / Spring Framework 7.0.10。
- Spring Security / Authorization Server 7.1.2。
- MyBatis Spring Boot Starter 4.1.0。
- Flyway MySQL 12.4.0。
- Spring Data Redis/Lettuce（用于 Valkey）。
- JSqlParser 5.4。
- Testcontainers 2.0.5（编译通过，新 MySQL artifact 为 `testcontainers-mysql`）。

Spring Security 7 将授权服务器 configurer 移入 `spring-security-config` 命名空间，Boot 4 也拆分了 Web MVC 测试 starter。PoC 已按当前 API 调整，不沿用旧示例导入。

### 租户与数据权限 SQL

`AccessQueryCompiler` 使用 JSqlParser AST 将已有业务条件与以下策略合并：

```text
tenantBoundary
AND (GrantA OR GrantB ...)
AND NOT MandatoryDeny
```

已验证参数化租户条件、完整授权并集、强制限制收口、未注册字段拒绝和根资源不匹配拒绝。UPDATE/DELETE 的记录 ID、乐观锁与权限条件在同一 SQL 合并，COUNT 复用列表 `AccessPlan`，固定 `LEFT JOIN` 的租户一致性条件保留在 `ON` 中。当前已验证 A 级和一个 B 级固定联表最小样例，不表示 C 级查询已验证。

### OAuth/OIDC

- OIDC discovery 端点在 Spring Boot 4.1.2 / Security 7.1.2 上可正常启动与访问。
- 原生移动客户端为无密钥公开客户端，强制 PKCE，带 S256 challenge 的完整授权请求可签发授权码。
- 集成客户端仅使用 Client Credentials。
- 移动和集成客户端的 Access Token 均配置为不透明 reference token；discovery 契约包含撤销端点。
- 未启用密码模式或 Implicit Flow。

### 字段权限与脱敏

- 普通授权只累加明确能力；可见、编辑、查询、排序、导出和打印不隐式相互包含。
- 强制限制只能降低展示级别和撤销能力，不能扩权。
- 编辑但无原文权时使用替换值语义，并区分未变更、清空和替换。
- 隐藏字段移出投影，公共字段须显式声明。

### 安全聚合与索引

- MySQL 真实容器验证了“先租户/公司数据范围，后 `GROUP BY`”的 C 级最小样例。
- `HAVING COUNT(*) >= threshold` 可隐藏低样本结果；具体 threshold 仍是业务/合规参数。
- 20,000 行样本下 MySQL `EXPLAIN` 选择 `(tenant_id, company_id, status)` 复合索引；该结果不替代目标数据量压测。

### 权限缓存与会话安全

- 权限缓存键绑定完整版本，权威版本变更时即使失效通知尚未到达也不使用旧缓存。
- `PermissionSnapshot` 在安全收紧后的关键操作再校验中能检测版本变化并中止。
- Valkey 不可用时权限 resolver 可回源权威数据库；Valkey 与数据库都无法建立权威结论时抛出不可用决策并默认拒绝。
- Valkey 8.1 Alpine 真实容器已验证 Lettuce 连接、TTL、原子版本递增与 compare-and-delete Lua。
- Web 写请求无 CSRF 被拒绝，有效 CSRF 通过；Cookie 配置已校验 `__Host-`、HttpOnly、Secure、SameSite=Lax、Path=/ 且无 Domain。
- Refresh Token 族支持单次轮换，旧 Token 重放会撤销整个设备会话族，后续即使提交当前 Token 也被拒绝。
- MySQL 持久化 Refresh Token 族通过 token hash/status 条件更新实现 CAS；两个并发刷新只有一个成功，另一个触发重放撤销。
- Web 显式装配 `ChangeSessionIdAuthenticationStrategy`，注销使会话失效。MockMvc 不模拟 Servlet 容器的 Cookie ID 变化，因此登录前后真实 Cookie ID 轮换仍需端口/浏览器 E2E。

### API 契约分面

- springdoc-openapi 3.2.0 在 Boot 4.1.2 上可生成 app、partner-v1、platform 三组契约。
- 测试确认各契约只包含所属入口的路径，外部契约不泄露第一方或平台管理路径。

### 密码基准

- Spring Security Argon2id 需显式依赖 Bouncy Castle `bcprov-jdk18on`，不能假设 starter 自动带入。
- Spring Security 5.8 默认 Argon2id 候选参数在当前 Apple Silicon/JDK 21 上单次 encode 约 62ms；生产参数仍须在目标 CPU/内存限额和并发压力下锁定。

### 前端 workspace

- Node 24.22.1 LTS + pnpm 11.25.0 下，pnpm workspace、Vue 3.5、Vite 8.3、TypeScript 6、Pinia 4 的类型检查、Vitest 和生产构建通过。
- `apps/admin` 只从 `@appnexus/organization` 和 `@appnexus/security` 公开入口装配，租户切换递增上下文版本的最小规则有单测。
- Node 26.13.0 LTS 使用临时官方运行时重放 `--frozen-lockfile`、类型检查、测试和构建均通过；Node 24 也已验证，可作回退。

### 契约工具链

- 从实际运行的 springdoc OpenAPI 3.1 契约成功使用 `openapi-typescript` 7.13.0 生成 TypeScript 类型。
- `openapi-diff` 0.24.1 不支持 OpenAPI 3.1，不应纳入 CI。`oasdiff` 1.35.0 能解析同一契约，并将删除 `/v1/organizations` 识别为 `api-path-removed-without-deprecation` 破坏性错误并返回非零状态。

### Outbox 与 MySQL 8.4

Outbox PoC 已在真实 MySQL 8.4 容器中通过：

- MySQL 8.4 Flyway 表结构与 `PLATFORM`/`TENANT` 作用域约束。
- `SELECT ... FOR UPDATE SKIP LOCKED` 短事务分批领取。
- Worker 租约、尝试次数和租约到期。
- `(event_id, handler_id)` 消费幂等唯一约束。
- 两个 Worker 领取的批次互不重叠。
- 真并发领取下单次空批次会短暂退避重试，最终领取完整且不重叠。
- Worker 租约过期可由其他 Worker 恢复，业务事务回滚后 Outbox 记录不存在。

本机无法拉取 Ryuk 镜像，本次显式禁用 Ryuk；Testcontainers 仍正常停止了 MySQL 容器。CI 在网络可用时应启用 Ryuk 资源清理。本机复现命令：

```bash
cd backend
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify -Pcontainer-tests
```

## 命令

```bash
cd backend
./mvnw test
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify -Pcontainer-tests
```

## 后续

1. 在目标部署资源限额下执行 Argon2id 并发压测和更大数据量 SQL 基准。
2. 使用真实端口/浏览器 E2E 验证登录/MFA/租户切换的 Cookie ID 轮换、BFF Token 隐藏和旧标签页响应丢弃。
3. 将 OAuth 授权、会话和撤销全部落到 MySQL/Valkey 持久化并验证应用重启/双故障语义。
4. 在 CI 固定 `openapi-typescript` 与 `oasdiff` 版本，补充 OpenAPI lint 规则和 SDK 构建。
