/**
 * 职责：平台对象目录、配置、交互绑定及编码的公开用例与无框架装配入口。
 * <p>边界：外部只使用本根包、明确的 port 及稳定 contracts；禁止导入 internal。
 * BusinessObjectUseCases 仅连接端口和内部实现，不创建数据库或框架对象。
 * <p>当前状态：从独立业务对象工程归入平台，仍不拥有具体业务对象正文及状态机。
 */
package com.appnexus.platform.businessobject;
