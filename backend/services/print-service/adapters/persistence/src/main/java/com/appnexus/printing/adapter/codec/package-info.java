/**
 * 打印内容、场景、载荷和回执的 Jackson 编解码及安全清洗。
 * <p>职责：打印内容、场景、载荷和回执的 Jackson 编解码及安全清洗；当前类型：JacksonPrintPayloadContent、JacksonPrintRuntimeContent、JacksonPrintSceneContent、PrintJson。
 * <p>公开边界与依赖：允许本服务内容输出端口、领域规则及必要的 Jackson 或 ImageIO 技术；供本服务装配使用，禁止执行表达式、访问数据库或自行决定业务权限。
 * <p>迁移状态：输出用例技术边界已分离，等待全量统一验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.printing.adapter.codec;
