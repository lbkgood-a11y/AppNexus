/**
 * 商品属性规则与内容快照的 Jackson 编解码适配。
 * <p>职责：商品属性规则与内容快照的 Jackson 编解码适配；当前类型：JacksonAttributeRuleContent、JacksonProductContent。
 * <p>公开边界与依赖：允许本服务内容输出端口、领域规则及必要的 Jackson 或 ImageIO 技术；供本服务装配使用，禁止执行表达式、访问数据库或自行决定业务权限。
 * <p>迁移状态：属性规则解析已从业务用例分离，待统一验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.codec;
