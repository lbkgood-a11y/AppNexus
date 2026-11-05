# API 命名与生命周期管理规范

版本：1.0；日期：2026-11-06；状态：已确认，作为 API 命名、契约、版本与废弃治理基线。

## 1. 目标与原则

- URL 表达业务资源和层级，不表达 Web、Mobile、Integration 等调用端类型。
- 接口面、认证方式和生命周期通过域名、OpenAPI 契约、客户端身份和版本策略区分，不在每个资源路径中重复渠道名。
- 同一业务概念在各接口面使用同一资源词汇；映射的 DTO、可见字段、认证和兼容承诺可以不同。
- 规范是编码、评审、OpenAPI 校验、SDK 生成、变更审批与废弃的共同依据。

## 2. API 接口面与入口

| 接口面 | 生产建议域名 | 公开基路径 | 契约 | 生命周期 |
|---|---|---|---|---|
| 第一方应用 API | `app-api.<domain>` | `/api/...` | `openapi-app.yaml` | 与受支持的 Web/移动客户端协同演进 |
| 外部合作 API | `api.<domain>` | `/v1/...` | `openapi-partner-v1.yaml` | 独立兼容承诺、废弃窗口和 SDK |
| 平台管理 API | `platform-api.<domain>` | `/api/...` | `openapi-platform.yaml` | 与平台运维客户端协同演进，不对租户/合作方开放 |

私有化部署可以使用不同实际域名，但三个逻辑入口和契约必须保持分离。第一期即使由同一应用进程提供，也通过反向代理/入口配置限定 Host、认证方式和可暴露契约。内部路由实现不构成对外契约。

Web 与原生移动端默认共用“第一方应用 API”。仅当交互、弱网、同步或返回模型明显不同时增加用途明确的 BFF/资源，不因客户端是手机就复制全套 API。

## 3. URL 命名

### 3.1 资源路径

- 使用英文名词，资源集合使用复数：`/organizations`、`/tenant-members`、`/purchase-orders`。
- 路径段使用小写 kebab-case，不使用动词开头、驼峰、下划线或不必要缩写。
- 单个资源使用 `/resource-collection/{id}`；ID 是标识，业务编码作为字段或明确查询条件，不混用。
- 层级路径只用于强所有关系，且一般不超过两层：`/orders/{orderId}/lines`。可独立定位的资源优先保留顶级入口。
- URL 不包含 `web`、`mobile`、`integration`、`internal`、`controller`、`service` 等实现或渠道词。

### 3.2 HTTP 方法

| 意图 | 方法 | 示例 |
|---|---|---|
| 列表/详情 | `GET` | `GET /api/organizations` |
| 创建 | `POST` | `POST /api/organizations` |
| 局部更新 | `PATCH` | `PATCH /api/organizations/{id}` |
| 完整替换（少用） | `PUT` | `PUT /api/configurations/{id}` |
| 删除 | `DELETE` | `DELETE /api/organizations/{id}` |

`GET`、`HEAD`、`OPTIONS` 无业务副作用。`POST` 创建返回 `201 Created` 和 `Location`；异步长任务返回 `202 Accepted` 和可查询任务资源。

### 3.3 非 CRUD 动作

- 能建模为资源或状态变更时优先资源建模，不滥用动作接口。
- 确实是命令的使用 `POST /resources/{id}:verb` 或集合动作 `POST /resources:verb`，动作名使用简短英文动词：`POST /orders/{id}:approve`。
- 同一动作在所有接口面使用同一业务词汇。

## 4. 查询、分页与排序

- 查询参数使用 lowerCamelCase：`organizationId`、`createdFrom`、`createdTo`。
- 分页统一使用 `page`/`pageSize` 或光标 `pageToken`/`pageSize`，一个契约内不混用多套命名。大数据变动集合优先不透明光标。
- 排序使用统一 `sort`，只接受资源契约已声明的字段/方向。筛选与查询能力受字段权限限制。
- 不接受请求传入 SQL、列名、表名或自由表达式。

## 5. JSON、标识与时间

- JSON 字段使用 lowerCamelCase；稳定枚举使用文字字符串，不暴露 Java 枚举序号。
- ID 在 HTTP/JSON 中使用标准 UUID 字符串。金额不使用浮点，同时携带币种；日期使用 `YYYY-MM-DD`，时刻使用 RFC 3339 并包含偏移或 `Z`。
- 未设置、显式 `null`、空字符串和空集合语义分别定义；PATCH 默认使用 JSON Merge Patch 或项目统一的显式变更集，不使用脱敏占位值表示“未修改”。

## 6. 版本策略

### 6.1 `v1` 是否必须

`v1` 不是 HTTP、REST 或 OpenAPI 的必须要求。成熟平台同时存在路径主版本、请求头日期版本和账号绑定版本等方式。版本机制的目的是管理已发布契约的不兼容变更，不是为 URL 装饰一个数字。

AppNexus 采用分面策略：

- 第一方应用 API 和平台管理 API 的公开路径不包含 `v1`，以 OpenAPI 契约、客户端最低支持版本和协同发布管理兼容性。若未来需同时长期支持不兼容的第一方契约，再经架构决策引入主版本路由。
- 外部合作 API 从首次稳定发布起在路径显式包含主版本 `/v1`。相容功能在 v1 内演进；只有无法兼容的变更才新建 `/v2`，且 v1/v2 须在承诺窗口内并行。
- 不使用 `/v1.1`、`/v1.2` 或补丁版路径。小版本是兼容演进，通过 OpenAPI/SDK/发布记录管理。

### 6.2 兼容与破坏性变更

通常兼容：增加可选字段、新资源/操作、新的可选筛选能力，以及事先声明可扩展的枚举返回新值。

通常破坏：删除/改名字段或资源，改变类型、必填性、默认值或语义，收紧原有请求可接受值，改变认证模型、幂等语义、分页方式或错误码含义。安全修复可在不升主版本时收紧错误放行，但须发布通知和迁移指引。

## 7. 契约、发布与废弃管理

- 每个接口面独立生成 OpenAPI 契约，并在 CI 中与上一个已发布版本做破坏性变更检查。不将内部 Controller 或未批准资源误放入外部契约。
- 外部 API 的每次发布记录契约摘要、变更类型、SDK 版本和生效日期。废弃须先标记 OpenAPI `deprecated`，再发布公告、替代方案、迁移指引、停用日期和调用方清单。
- 外部 API 响应可在废弃阶段使用标准 `Deprecation`/`Sunset` 等响应头提示，不以突然删除代替治理。
- 破坏性变更须提交 API 决策记录，说明动机、影响调用方、兼容替代、并行期和回退方案。

## 8. 认证、租户与权限命名

- 第一方 Web 使用服务端会话/Cookie，原生移动端使用 Authorization Code + PKCE，外部合作 API 使用 Client Credentials；认证差异不改变资源 URL。
- 租户标识从已验证会话/Token 与权威状态获取，不依赖 URL 中的渠道名。若某资源的公开身份本身包含租户层级，可使用明确资源名层级，但仍须校验身份绑定。
- 权限 `resourceId`/`actionId` 使用稳定点分词业务标识，例如 `org.unit:view`、`order.purchase:approve`；不与 URL、Controller 类名或前端路由强绑。

## 9. 强制检查

- OpenAPI lint 检查路径大小写、复数资源、HTTP 方法、错误契约、操作 ID 和安全声明。
- CI 检查破坏性变更、生成客户端是否可构建、外部契约是否泄露内部字段以及接口权限声明是否完整。
- 命名例外、D 级查询、新主版本或缩短外部废弃窗口均须架构/安全评审并记录理由。

## 10. 参考规范

- [Google API Improvement Proposals：资源导向设计](https://google.aip.dev/121)、[资源名](https://google.aip.dev/122)、[版本](https://google.aip.dev/185)
- [Microsoft Azure REST API Guidelines](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md)
- [GitHub REST API versions](https://docs.github.com/en/rest/about-the-rest-api/api-versions)
- [Shopify API versioning](https://shopify.dev/docs/api/usage/versioning)
- [Stripe API versioning](https://docs.stripe.com/api/versioning)
