# 打印代理

2026-11-11：代理现支持 HTTP 与 HTTPS；旧文中“强制 HTTPS”不再适用。Windows 可从网页“在线客户端打印机”下载客户端压缩包，在客户端窗口填写连接配置，无需用户手动配置环境变量。具体步骤与边界见 [客户端说明](client/README.md)。HTTP 不加密传输，不改变凭据权限与租户边界。

代理部署在能访问目标系统打印队列的站点机器上，主动领取当前租户、当前站点的任务。模板与授权数据快照在本机受控 Chromium 中渲染为临时 PDF，提交队列后立即删除；不保存业务会话或打印内容。

## 配置

| 环境变量 | 用途与约束 |
| --- | --- |
| `PRINT_API_URL` | 平台 HTTPS 根地址，必填、无默认值；生产不得使用明文 HTTP。 |
| `PRINT_TOKEN_URL` | OAuth 2.0 Client Credentials Token 地址，必填；客户端必须绑定单一租户和专用 `print-agent:operate` scope。该 scope 只允许代理领取与回执路径，不授予人员 API。 |
| `PRINT_CLIENT_ID` / `PRINT_CLIENT_SECRET` | 代理客户端凭据，必填、无默认值；由部署密钥设施注入，禁止写入参数文件或日志。 |
| `PRINT_TENANT_CONTEXT_VERSION` | OAuth 租户上下文版本，默认 `1`；租户绑定变化后由管理员更新。 |
| `PRINT_SITE_CODE` | 代理站点编码，必填，1—64 位；只领取同编码设备任务。 |
| `PRINT_AGENT_ID` | 代理实例稳定标识，必填，最长 128 位；不得包含主机凭据。 |
| `PRINT_CHROMIUM_PATH` | 经验证的 Chromium/Chrome 可执行文件绝对路径，必填；升级后重新执行字体和版式验收。 |
| `PRINT_SUMATRA_PATH` | Windows 必填的 SumatraPDF 可执行文件绝对路径；Linux/macOS 不使用。 |
| `PRINT_POLL_INTERVAL_MS` | 领取间隔，单位毫秒，默认 `3000`，范围 `1000—60000`；缩短会增加平台压力。 |

Linux/macOS 使用 `lp`，Windows 使用 SumatraPDF 的显式 `-print-to`。代理不通过 shell 拼接命令。系统队列接受只回报 `SUBMITTED`，不宣称纸张已经完成；渲染后状态不明回报 `UNKNOWN`，禁止自动补打。

## 客户端选择打印机与跨平台部署

### 同一电脑多台打印机

1. 在操作系统中安装各打印机驱动，并用系统测试页确认每个队列可用。USB、网络打印机分别登记为系统队列，使用清晰且唯一的队列名。
2. 在该电脑配置并运行一个打印代理。`PRINT_SITE_CODE` 应标识这台电脑或只由该电脑服务的站点，避免不同电脑使用相同站点编码而领取彼此无法访问的队列；`PRINT_AGENT_ID` 为稳定实例标识。
3. 代理每 60 秒发现并上报全部本机队列。网页“在线客户端打印机”显示站点、实例和每个队列，每个队列单独点击“登记设备”。同一代理不必为每台打印机重复启动。
4. 在“录入打印”选择“打印到设备”，选择已登记打印机和份数。后端固定设备，代理用 Windows SumatraPDF `-print-to` 或 Linux/macOS `lp -d` 调用该系统队列，操作系统驱动再控制打印机。
5. PDF 输出不选设备和份数，交给当前租户任一认证渲染代理生成附件；仍需要部署在线渲染代理，不会在浏览器中模拟成功。PDF 任务不调用打印机队列。

代理首次部署仍需要管理员提供 HTTPS 平台/Token 地址及绑定租户的专用 Client Credentials。凭据只注入代理进程，禁止在网页、模板或设备名称中填写。Windows 物理打印需 SumatraPDF，三种系统均需受支持 Chromium 和字体。系统接受只表示已提交队列，故障和 UNKNOWN 应先查队列，不能反复提交。

先在实际连接设备的客户端执行 `pnpm start --list-printers`；该只读命令不要求平台凭据，不自动登记或授权设备。选择结果中的 `name`，在平台设备管理中填写相同的系统队列名称以及当前代理站点。客户端选择明确队列后才提交任务，代理不回退操作系统默认打印机。

| 系统 | 发现与提交依赖 | 当前验证边界 |
| --- | --- | --- |
| Windows | PowerShell `Get-Printer`；已安装的打印队列；部署指定 SumatraPDF | 本机发现、Edge 渲染验证；实体设备待验收 |
| Linux | CUPS 客户端 `lpstat` / `lp`；目标驱动与字体；受支持 Chromium | 命令构造与解析测试通过，目标发行版实机待验收 |
| macOS | 系统 `lpstat` / `lp`、已授权队列、兼容 Chromium | 命令构造与解析测试通过，本轮未在 macOS 实机运行 |

平台地址与 Token 地址强制 HTTPS，禁止 URL 内嵌凭据、查询参数和重定向；Chromium 与 Windows SumatraPDF 路径必须为绝对路径。Windows 启动前校验 SumatraPDF 必填。轮询间隔拒绝 NaN、小数和越界值，租户上下文版本必须为正安全整数。

渲染上下文禁用 Service Worker、下载并拦截所有网络请求，每次渲染最多 60 秒；队列命令最多 30 秒。两者均为内部固定上限，不新增环境变量。平台领取租约当前为 120 秒；超时不会触发自动重打。每次渲染使用独立上下文、加载本地打印 CSS 并等待字体就绪，结束后关闭上下文。

渲染失败和打印程序未启动时回报 FAILED。打印程序启动后异常退出或超时、队列提交后回执失败均按 UNKNOWN 处理，不将非零退出误判成“确定没有出纸”。日志不输出驱动 stderr。

## 验证命令与严格 JSON 参数

Node 使用仓库支持的 24—26 版本；`pnpm install --frozen-lockfile`、`pnpm build`、`pnpm test`。设置 `PRINT_TEST_CHROMIUM_PATH` 为测试 Chromium 绝对路径后，测试额外验证实际 A4 PDF、出仓单样本和超时清理；该变量只作用于测试，无默认值，不配置则跳过浏览器测试。

package.json 的 scripts.build 只做类型检查，scripts.test 通过 tsx 执行 Node 测试，不连接平台或调用实体打印队列；scripts.start 启动代理，传入 `--list-printers` 时只发现队列。private=true 防止误发布，type=module 使用 ESM，engines.node 限制已支持运行时；依赖以 pnpm-lock.yaml 锁定，升级需重新验证渲染和队列行为。
