# AppNexus backend

The formal platform service uses modular domain boundaries and service-level adapters.
Formal features are implemented under services/platform-service. poc-app contains isolated
historical technical verification and is not a runtime dependency.

## Implemented login foundation

- MySQL 8.4 account/password persistence through MyBatis; Flyway migrations.
- UUIDv7 immutable account identifiers, unique case-insensitive usernames, Argon2id hashes.
- One-time administrator bootstrap, atomically claimed in the database; restarting does not reset credentials.
- Embedded OIDC authorization server and confidential BFF, Authorization Code + PKCE S256.
- JDBC client, authorization and consent repositories. BFF authorized-client state stays in the server session.
- Shared Valkey sessions; HttpOnly, SameSite=Lax cookies; Secure enabled outside local HTTP.
- Persistent PKCS12 RSA signing key; no runtime ephemeral key generation.
- Account status checked on BFF access and authorization/userinfo requests.
- Valkey login throttling: default 60 attempts/IP/minute and 10 attempts/account+IP/minute.
  Limits are configurable through app.login.max-attempts-per-ip and app.login.max-attempts-per-account-ip.
  Direct peer IP is used; trusted proxy forwarding is not yet configured.
- CSRF-protected form login/logout. The Vue template provides the form; credentials post to the authorization server.
- GET /bff/session returns a direct display DTO, with no Token or password hash; GET /bff/csrf and POST /bff/logout.
  Roles/permissions are currently empty: login does not grant business capabilities.

## Local development (PowerShell, Java 21 / Node 24–26)

Run from the repository root in the terminal that will start the backend:

```powershell
./scripts/Initialize-Local.ps1
docker compose up -d --wait
cd backend
mvn -pl services/platform-service/application -am install -DskipTests
mvn -pl services/platform-service/application spring-boot:run "-Dspring-boot.run.profiles=local"
```

The initializer creates persistent local credentials and signing material in ignored .local/.
The generated bootstrap username/password are in .local/environment.json. They are not logged.
For a fresh local database the username defaults to `admin`; the same initialization run
creates the login account first and then grants its initial platform-administrator identity.
This file is a local development convenience; deployment secrets must be supplied by the deployment secret store.
Keep .local/ when restarting: the database and signing key must retain matching credentials.
Bootstrap variables apply only to first initialization; changing them does not reset an existing account.

In another terminal:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm --filter @appnexus/admin dev
```

Open http://localhost:8848. Use localhost consistently. Vite proxies OAuth and BFF endpoints
to backend port 8080. Infrastructure binds only to loopback: MySQL 3307, Valkey 6380.
Local disables Secure cookies for HTTP; production must use HTTPS.

## Deployment configuration

Required environment variables: APP_DATABASE_URL, APP_DATABASE_USERNAME, APP_DATABASE_PASSWORD,
APP_VALKEY_HOST, APP_VALKEY_PORT, APP_VALKEY_PASSWORD, APP_BFF_SECRET, APP_PUBLIC_URL,
APP_FRONTEND_URL, APP_SIGNING_KEY_STORE, APP_SIGNING_KEY_PASSWORD.
APP_SIGNING_KEY_ALIAS defaults to appnexus. APP_BOOTSTRAP_USERNAME and APP_BOOTSTRAP_PASSWORD
are optional and must be supplied together for first provisioning. A production first deployment
sets APP_BOOTSTRAP_USERNAME to `admin`, with the random initial password supplied by the deployment
secret store. The same account receives the initial platform-administrator identity. Remove the bootstrap username after
successful initialization; they are not account-recovery controls.
The JDBC URL must include preserveInstants=true, connectionTimeZone=UTC and
forceConnectionTimeZoneToSession=true. The key store must contain an RSA private key and certificate.
All replicas must share database, Valkey namespace, BFF registration and signing material.
Changing persisted client secret or callback requires an explicit configuration migration.
Flyway auto-migration is enabled only in local. Production must run the versioned SQL scripts
with a single controlled migration executor before starting applications; APP_FLYWAY_ENABLED
can explicitly enable a migration run. Applications do not run clean.

## Verification

```powershell
mvn -pl services/platform-service/application -am test
mvn -pl services/platform-service/architecture-tests -am test
```

Application integration tests require Docker and start actual MySQL 8.4 and Valkey containers.
They cover authorization-code exchange, PKCE, session ID rotation, a second application instance
accepting the same cookie, logout invalidation, credentials, CSRF, bootstrap idempotency,
account disabling and shared rate limits. The first browser acceptance also passed login,
layout rendering, refresh session recovery, UI logout and access rejection after logout;
no password or Access/Refresh Token was found in browser storage.

Remaining framework work includes account administration/password recovery, MFA, device/session
management, absolute session lifetime, audit events, tenant selection and business authorization.
The initial 30-minute idle timeout and Argon2id defaults remain subject to deployment benchmarking.
Database/Valkey failure fallback, signing-key rotation and production capacity/fault acceptance remain outstanding.

## Personal account settings

The account menu opens /#/account/profile. GET /bff/account returns the current profile;
PATCH /bff/account updates only displayName. POST /bff/account/password accepts
currentPassword and newPassword with a session-bound CSRF header. Password changes increment
the credential version and revoke OAuth authorizations atomically. Old sessions are denied
on their next request. Upgrading from sessions without a credential-version marker requires
signing in again. Display-name changes keep existing sessions valid.

The password length is 12–128 UTF-16 code units; identical passwords, account names and
a small built-in list of common passwords are rejected. Full breached-password screening,
MFA and security audit remain separate framework work.

## Personal security log

GET /bff/account/security-logs?page=1&size=10 returns items and total for the authenticated
account only (size <= 50). Successful password sign-in, explicit logout, profile updates and
password changes append minimal records to account_security_log (Flyway V3).
Profile/password changes and their records share a MySQL transaction. No raw credentials,
request bodies, location or device metadata are stored. Failed attempts and expiry are not
yet included; retention, archival and tamper-resistant storage remain future audit work.

## Sign-in sessions

GET /bff/account/sessions lists this account's active credential-version-valid sessions.
DELETE /bff/account/sessions/{handle} revokes another session with CSRF protection.
Handles are SHA-256 management identifiers, never raw session credentials. Cross-account
and missing targets return 404; current-session deletion returns 409. Indexed Valkey
session repository is configured through spring.session.data.redis.repository-type.
Pre-upgrade sessions may require sign-in again to populate the index. In-flight requests,
full device metadata, bulk/admin revocation and revocation audit are outside this increment.

## Tenant-scoped SQL boundary

The application installs a MyBatis tenant SQL gate. A tenant Mapper must opt in with
@TenantScoped(table="...") and use the exact supported single-table CRUD SQL shape with
a dedicated __verifiedTenant binding. The executor replaces that binding with the immutable
BINARY(16) tenant ID from the verified request context. Missing context, unsupported SQL,
joins, subqueries, OR/UNION, dynamic tables, batches, tenant reassignment and unmarked tenant
mappers fail closed. MyBatis result caches are disabled and SIMPLE executor is enforced.
A real MySQL A/B same-ID CRUD isolation test exists under application tests; its fixture table
is created only by the test. This is a strict first slice, not a general SQL rewriter.
Do not register production tenant mappers with unsupported shapes or bypass MyBatis using JDBC.
Native SQL, reports, background jobs, tenant-aware join projections and write-boundary coverage
require their own reviewed adapters and integration tests before use.

## Tenant SQL enforcement scope declaration

MyBatis mappers are deny-by-default: every mapper must explicitly declare either
@TenantScoped(table="...") and match the supported single-table CRUD shapes, or
@PlatformScoped for reviewed platform/identity metadata queries. A new or unclassified mapper
fails at execution. PlatformScoped is restricted to platform-level metadata and membership
resolution; never use it for ordinary tenant-owned business data. Authorization decisions,
join/report models, asynchronous jobs and native JDBC require separately reviewed boundaries.


Mapper scope is deny-by-default. @TenantScoped limits tenant tables to a small fixed CRUD grammar;
@PlatformScoped carries an exact table allowlist that is parsed from each SQL statement at execution.
SQL parse failure and any table outside the declared list fail closed. This is in addition to the
tenant guard and does not grant business authorization. Any new platform mapper must declare its
minimal table set and add a real-MySQL test proving out-of-scope references are rejected.


组织持久化首批：新增 OrganizationUnit 不可变模型和仓储公开端口，V5 迁移创建 tenant_organization_unit，复合主键为 (tenant_id,id)，名称租户内唯一。正式 MyBatis Mapper 支持当前租户列表、新建、改名、删除；SQL 白名单仅增加对应固定形状，专用租户绑定仍由验证上下文覆盖。真实 MySQL 测试以 A/B 租户相同 UUID 验证列表、更新与删除隔离。

组织操作授权、组织类型/树关系、并发版本及操作审计尚未实现，因此本批不注册组织业务 HTTP API 或前端菜单；有效租户成员身份不等同于组织管理权限。本批是组织隔离仓储增量，不是完整组织管理功能。

Organization permissions are deny-by-default. The authorization module defines READ/CREATE/UPDATE/DELETE capabilities and persists explicit tenant-member grants. The shared tenant execution filter checks the method-specific capability before any organization route or tenant Mapper can run. No grant-management endpoint or implicit administrator grant exists yet; organization CRUD routes and menu remain closed until an authorized grant-management workflow is implemented and audited.


组织授权生命周期增量：新增 GRANT 管理能力，以及 V7/V8/V9 迁移（撤销操作者、追加审计、GRANT 类型）。POST /api/organization/grants 与 DELETE /api/organization/grants/{memberId}/{capability} 使用服务端租户成员上下文校验 GRANT，复核目标有效成员并在同一 MySQL 事务写授权和审计。仅允许 READ/CREATE/UPDATE/DELETE，管理能力不可通过接口委派；GET 列表也要求 GRANT。没有默认授予或首个成员特权，初始管理者来源仍需受控运维治理，本批不提供自助初始化入口或授权 UI。

授权管理存在性查询使用明确表白名单；普通成员调用与跨租户目标默认拒绝。此为组织能力授权子集，不代表完整 AccessPlan、角色继承、数据/字段权限或授权委派产品已完成。授权变更在事务内固定顺序锁定租户、目标/操作者成员和 GRANT 授权行，避免校验后状态变化；并发容量与完整委派模型仍待后续验收。组织 CRUD API 继续不开放，授权管理只面向已由受控运维授予 GRANT 的成员。

组织管理员初始化与授权页面的部署流程见 [组织管理员初始化与授权验收](../docs/design/组织管理员初始化与授权验收.md)。默认不开启组织初始化；部署时通过 APP_ORGANIZATION_BOOTSTRAP_TENANT_ID 与 APP_ORGANIZATION_BOOTSTRAP_MEMBER_ID 显式指定已有有效租户和成员，成功后移除配置。认证账号初始化不会自动获得业务权限。

平台用户、租户开通与成员管理现已接通。全新部署设置 `APP_BOOTSTRAP_USERNAME=admin` 与随机 `APP_BOOTSTRAP_PASSWORD`，先创建登录账号再为同一账号授予首位平台身份；成功后移除用户名引导参数。配置默认空且不按固定用户名对存量账号自动赋权。运行 V11/V12 迁移后使用“平台管理”完成用户→租户→成员→组织归属/授权。详见[操作流程](../docs/design/租户用户成员组织操作流程.md)。
