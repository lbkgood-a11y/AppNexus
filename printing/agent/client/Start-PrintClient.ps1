<#
.SYNOPSIS
启动 AppNexus Windows 打印客户端配置窗口。
.DESCRIPTION
使用随包 Node 运行时启动代理，自动查找 Edge 和内置 SumatraPDF。
配置存于当前用户 LocalAppData，Secret 使用 Windows DPAPI 加密，仅当前 Windows 用户可解密。
不修改系统驱动、不创建共享凭据、不自动提交任何打印任务；关闭窗口同时停止本窗口启动的代理。
#>
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$clientRoot = Split-Path $PSScriptRoot -Parent
# 当前用户配置目录；不写入仓库或下载包，DPAPI 密文不具备跨用户可移植性。
$settingsRoot = Join-Path $env:LOCALAPPDATA 'AppNexus\PrintClient'
$settingsFile = Join-Path $settingsRoot 'settings.xml'
$script:agentProcess = $null
$form = New-Object Windows.Forms.Form
$form.Text = 'AppNexus 打印客户端'
$form.Size = New-Object Drawing.Size(660, 520)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.Font = New-Object Drawing.Font('Microsoft YaHei UI', 10)
$fields = @{}
$definitions = @(
    @('平台地址（支持 HTTP / HTTPS）','Api','http://localhost:8080'),
    @('客户端 ID','ClientId',''),
    @('客户端 Secret','Secret',''),
    @('本机站点编码','Site',('pc-' + $env:COMPUTERNAME.ToLower())),
    @('代理实例标识','Agent',('agent-' + $env:COMPUTERNAME.ToLower()))
)
$y = 20
foreach ($definition in $definitions) {
    $label = New-Object Windows.Forms.Label
    $label.Text = $definition[0]
    $label.SetBounds(20,$y,260,25)
    $input = New-Object Windows.Forms.TextBox
    $input.Text = $definition[2]
    $input.SetBounds(20,($y+26),600,28)
    if ($definition[1] -eq 'Secret') { $input.UseSystemPasswordChar = $true }
    $fields[$definition[1]] = $input
    $form.Controls.AddRange(@($label,$input))
    $y += 65
}
if (Test-Path -LiteralPath $settingsFile) {
    try {
        $saved = Import-Clixml -LiteralPath $settingsFile
        foreach ($name in @('Api','ClientId','Site','Agent')) { $fields[$name].Text = $saved[$name] }
        $fields.Secret.Text = [Net.NetworkCredential]::new('', $saved.Secret).Password
    } catch { [Windows.Forms.MessageBox]::Show('原配置无法读取，请重新填写。') | Out-Null }
}
$status = New-Object Windows.Forms.Label
$status.Text = '首次使用：在平台当前租户创建 print-agent:operate 集成客户端，然后填写身份。'
$status.SetBounds(20,350,600,45)
$start = New-Object Windows.Forms.Button
$start.Text = '保存并连接'
$start.SetBounds(20,405,150,35)
$stop = New-Object Windows.Forms.Button
$stop.Text = '停止'
$stop.SetBounds(180,405,100,35)
$queues = New-Object Windows.Forms.Button
$queues.Text = '查看本机打印机'
$queues.SetBounds(290,405,170,35)
$form.Controls.AddRange(@($status,$start,$stop,$queues))
$start.Add_Click({
    try {
        if ($script:agentProcess -and -not $script:agentProcess.HasExited) { throw '客户端已启动，请先停止。' }
        $api = [Uri]$fields.Api.Text.Trim().TrimEnd('/')
        if ($api.Scheme -notin @('http','https') -or $api.UserInfo -or $api.Query -or $api.Fragment) { throw '平台地址格式无效。' }
        if (-not $fields.ClientId.Text.Trim() -or -not $fields.Secret.Text) { throw '请填写客户端 ID 和 Secret。' }
        $browser = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Google\Chrome\Application\chrome.exe") | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
        if (-not $browser) { throw '未检测到 Edge 或 Chrome，请先安装浏览器。' }
        $runtime = Join-Path $clientRoot 'runtime\node.exe'
        $sumatra = Join-Path $clientRoot 'runtime\SumatraPDF.exe'
        if (-not (Test-Path -LiteralPath $runtime) -or -not (Test-Path -LiteralPath $sumatra)) { throw '下载包不完整，请重新下载并完整解压。' }
        New-Item -ItemType Directory -Path $settingsRoot -Force | Out-Null
        @{Api=$api.AbsoluteUri.TrimEnd('/');ClientId=$fields.ClientId.Text.Trim();Secret=(ConvertTo-SecureString $fields.Secret.Text -AsPlainText -Force);Site=$fields.Site.Text.Trim();Agent=$fields.Agent.Text.Trim()} | Export-Clixml -LiteralPath $settingsFile
        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = $runtime
        $info.Arguments = 'dist/main.js'
        $info.WorkingDirectory = $clientRoot
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        # 凭据通过子进程环境传入，不进入命令行参数、URL 或日志。
        $values = @{PRINT_API_URL=$api.AbsoluteUri.TrimEnd('/');PRINT_TOKEN_URL=($api.AbsoluteUri.TrimEnd('/')+'/oauth2/token');PRINT_CLIENT_ID=$fields.ClientId.Text.Trim();PRINT_CLIENT_SECRET=$fields.Secret.Text;PRINT_SITE_CODE=$fields.Site.Text.Trim();PRINT_AGENT_ID=$fields.Agent.Text.Trim();PRINT_CHROMIUM_PATH=$browser;PRINT_SUMATRA_PATH=$sumatra}
        foreach ($key in $values.Keys) { $info.EnvironmentVariables[$key] = $values[$key] }
        $script:agentProcess = [Diagnostics.Process]::Start($info)
        $status.Text = '代理已启动，队列将每分钟上报。请在平台“在线客户端打印机”登记各台设备。'
    } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message,'启动失败') | Out-Null }
})
$stop.Add_Click({ if ($script:agentProcess -and -not $script:agentProcess.HasExited) { $script:agentProcess.Kill() }; $status.Text='代理已停止。' })
$queues.Add_Click({
    try { $names = (Get-Printer -ErrorAction Stop).Name -join "`r`n"; [Windows.Forms.MessageBox]::Show($names,'本机系统打印队列') | Out-Null }
    catch { [Windows.Forms.MessageBox]::Show('无法读取本机打印机，请检查 Windows 打印服务。') | Out-Null }
})
$timer = New-Object Windows.Forms.Timer
$timer.Interval = 3000
$timer.Add_Tick({ if ($script:agentProcess -and $script:agentProcess.HasExited) { $status.Text = '代理进程已退出，请检查平台地址、身份和站点参数后重新连接。' } })
$timer.Start()
$form.Add_FormClosed({ $timer.Stop(); if ($script:agentProcess -and -not $script:agentProcess.HasExited) { $script:agentProcess.Kill() } })
[Windows.Forms.Application]::Run($form)
