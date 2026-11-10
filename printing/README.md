# 打印运行组件

状态：平台服务、管理页面和站点代理主链路已实现；真实业务数据契约接入、目标打印机验收及 Office 格式转换仍须随业务模块和部署环境完成。

- renderer：隔离的 Chromium/PDF 渲染组件。
- agent：Windows/Linux/macOS 站点代理；受控 Chromium 渲染，Windows 使用显式 SumatraPDF，Linux/macOS 使用 `lp`。
- validation：单据、输出、部署及设备验证记录。

此目录不纳入前端 pnpm workspace；代理独立安装和部署，配置及安全约束见 agent/README.md。

需求与准入条件见 [设计记录](../docs/design/打印服务设计与选型记录.md)。
