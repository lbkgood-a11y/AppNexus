/**
 * 商品图片格式、尺寸与像素限制的 ImageIO 内容处理。
 * <p>职责：商品图片格式、尺寸与像素限制的 ImageIO 内容处理；当前类型：ImageIoProductImageContent。
 * <p>公开边界与依赖：允许本服务内容输出端口、领域规则及必要的 Jackson 或 ImageIO 技术；供本服务装配使用，禁止执行表达式、访问数据库或自行决定业务权限。
 * <p>迁移状态：已拆出解码与重编码边界，等待全量统一验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.media;
