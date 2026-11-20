/**
 * 主数据 HTTP 错误契约与响应缓存策略。
 * <p>职责：主数据 HTTP 错误契约与响应缓存策略；当前类型：MasterDataCachePolicy、MasterDataErrors。
 * <p>公开边界与依赖：允许稳定错误类型与 Spring Web 响应扩展；仅负责 HTTP 错误和缓存语义，禁止查询存储、吞掉业务失败或改变授权。
 * <p>迁移状态：类型归属已迁移，存量用例仍须依实际调用链复核，尚未统一运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.web;
