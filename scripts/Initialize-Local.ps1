<#
.SYNOPSIS
初始化正式平台应用的本机开发环境。
.DESCRIPTION
在当前 PowerShell 进程设置数据库、Valkey、BFF 和签名密钥参数。
首次生成随机凭据并保留到已被 Git 忽略的 .local/environment.json，后续优先沿用已有值。
该文件仅用于本机开发，不代替生产密钥管理，也不会重置已经初始化的数据库账号。
.NOTES
应在后续启动后端的同一终端执行；依赖 Java keytool，随机密钥均不打印。
新增 MFA 参数及严格 JSON 凭据项中文说明见 docs/design/第一期收尾执行记录.md 的“配置与部署”。
#>
$ErrorActionPreference = "Stop"
$workspaceRoot = Split-Path $PSScriptRoot -Parent
$localDirectory = Join-Path $workspaceRoot ".local"
# 本机凭据文件路径，已由 .gitignore 排除；不得提交或复制到公开位置。
$environmentFile = Join-Path $localDirectory "environment.json"
New-Item -ItemType Directory -Force -Path $localDirectory | Out-Null
if (Test-Path -LiteralPath $environmentFile) {
    $saved = Get-Content -Raw -LiteralPath $environmentFile | ConvertFrom-Json -AsHashtable
    foreach ($name in $saved.Keys) {
        if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
            [Environment]::SetEnvironmentVariable($name, $saved[$name], "Process")
        }
    }
}
$secrets = @{}
foreach ($name in @("APP_DATABASE_PASSWORD", "APP_DATABASE_ROOT_PASSWORD", "APP_VALKEY_PASSWORD",
                    "APP_BFF_SECRET", "APP_BOOTSTRAP_PASSWORD", "APP_SIGNING_KEY_PASSWORD",
                    # MFA 专用 AES-256 密钥：缺省生成 32 随机字节并以 Base64 持久保留，仅本机开发；重启不得轮换，生产使用部署密钥存储。
                    "APP_MFA_ENCRYPTION_KEY")) {
    if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
        # 每个凭据使用 32 字节密码学随机数，Base64 编码后保存；单位为字节。
        $randomBytes = [byte[]]::new(32)
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
        [Environment]::SetEnvironmentVariable($name, [Convert]::ToBase64String($randomBytes), "Process")
    }
    $secrets[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}
# 首次平台管理员登录名默认 admin；仅在数据库尚未初始化时创建账号并授予平台身份，不作为重置入口。
if (-not $env:APP_BOOTSTRAP_USERNAME) { $env:APP_BOOTSTRAP_USERNAME = "admin" }
$secrets["APP_BOOTSTRAP_USERNAME"] = $env:APP_BOOTSTRAP_USERNAME
$secrets | ConvertTo-Json | Set-Content -LiteralPath $environmentFile
# MySQL 使用本机 3307 端口和 UTC 时间语义，与 Compose 的端口映射保持一致。
$env:APP_DATABASE_URL = "jdbc:mysql://localhost:3307/appnexus?preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
# 应用数据库账号固定为 appnexus，与 Compose 初始化账号一致。
$env:APP_DATABASE_USERNAME = "appnexus"
# 本机 Valkey 主机，默认 localhost；仅用于开发环境。
$env:APP_VALKEY_HOST = "localhost"
# Valkey 本机端口 6380，对应容器内部 6379。
$env:APP_VALKEY_PORT = "6380"
# 浏览器访问授权服务器的公开地址，使用前端开发代理 8848 端口，不带末尾斜杠。
$env:APP_PUBLIC_URL = "http://localhost:8848"
# 登录成功或失败后返回的前端地址，与浏览器访问的主机名保持一致。
$env:APP_FRONTEND_URL = "http://localhost:8848"
# 持久 PKCS12 文件路径；存在时沿用，不随应用重启重新生成。
$env:APP_SIGNING_KEY_STORE = Join-Path $localDirectory "signing.p12"
if (-not (Test-Path -LiteralPath $env:APP_SIGNING_KEY_STORE)) {
    # 首次生成 RSA 3072 位密钥，别名 appnexus，证书有效期 365 天；口令从环境变量读取。
    & keytool -genkeypair -alias appnexus -keyalg RSA -keysize 3072 -storetype PKCS12 -keystore $env:APP_SIGNING_KEY_STORE -storepass:env APP_SIGNING_KEY_PASSWORD -dname "CN=AppNexus Local" -validity 365 -noprompt
    if ($LASTEXITCODE -ne 0) { throw "Signing key initialization failed" }
}
Write-Host "Local environment initialized. Secrets are stored in ignored .local/environment.json for local development only."
