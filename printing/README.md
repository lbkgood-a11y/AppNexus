# 打印运行组件

状态：工程目录准备，均未实现。平台 Java 服务位于 backend/services/print-service，设计器入口预留于 frontend/modules/print。

- renderer：隔离的 Chromium/PDF 渲染组件。
- agent：Windows/Linux/macOS 设备适配候选与打印代理。
- validation：单据、输出、部署及设备验证记录。

此目录不纳入前端 pnpm workspace；没有 package.json、安装依赖、系统配置或可运行进程。Node/Electron 版本在安全与目标环境验证后锁定。

需求与准入条件见 [设计记录](../docs/design/打印服务设计与选型记录.md)。
