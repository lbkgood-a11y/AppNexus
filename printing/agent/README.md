# 打印代理

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
