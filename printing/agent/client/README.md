# Windows 打印客户端

解压下载包后运行 `client/Start.cmd`，配置窗口填写平台 HTTP/HTTPS 地址、专用客户端 ID/Secret 和本站点编码，点击保存并连接。自动检测 Edge/Chrome；下载包包含 Node 和 SumatraPDF，不要求安装 Node 或设置环境变量。操作系统仍须安装对应打印机驱动。

同一电脑运行一个代理，自动上报所有打印队列。网页“在线客户端打印机”刷新后分别登记，打印时选择目标设备。关闭配置窗口停止本窗口启动的代理；当前没有开机自启和安装器。

配置保存在 `%LOCALAPPDATA%/AppNexus/PrintClient/settings.xml`，Secret 使用当前 Windows 用户 DPAPI 加密。不能将该文件复制给其他用户。下载包不包含平台凭据；首次绑定仍需要对应租户的 `print-agent:operate` 客户端身份。

HTTP 已按用户要求支持，HTTP 链路不提供传输加密，适用于用户控制的本机/内网。地址、密钥只在本机配置，不将密钥传入命令行或网页下载链接。程序仍禁止 URL 内嵌凭据和重定向。

下载包使用 Node 24.22.1、SumatraPDF 3.5.2、仓库锁定代理依赖。SumatraPDF 来源：https://www.sumatrapdfreader.org/ ，许可证与源码：https://github.com/sumatrapdfreader/sumatrapdf 。Node 许可证与源码：https://github.com/nodejs/node 。HiPrint 许可与商业分发限制见 frontend/modules/print/THIRD_PARTY_NOTICES.md。当前包供本机开发验证，未签名，尚未完成商业分发许可和三系统安装包验收。
