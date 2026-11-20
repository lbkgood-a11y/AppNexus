/**
 * 授权模板固定 SQL 与模板存储端口实现。
 * <p>职责：授权模板固定 SQL 与模板存储端口实现；当前类型：AuthorizationTemplateMapper、MyBatisPolicyTemplateStorage。
 * <p>公开边界与依赖：允许本服务稳定契约、输出端口、MyBatis 与必要的存储技术；供本服务用例及装配接入，禁止提供任意 SQL、读取其他业务私表或自行决定授权。
 * <p>迁移状态：完成职责命名与包迁移，完整运行及安全验证在全量迁移后统一执行。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.persistence.authorization;
