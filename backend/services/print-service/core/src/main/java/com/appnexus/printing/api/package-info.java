/**
 * 打印运行、场景与输出的公开用例及稳定数据。
 * <p>职责：打印运行、场景与输出的公开用例及稳定数据；当前类型：PrintData、PrintOutputManagement、PrintRuntimeManagement、PrintSceneManagement。
 * <p>公开边界与依赖：允许 JDK 与稳定公开业务契约；作为本服务对外调用边界，不暴露技术存储或内部实现，禁止依赖 Spring、SQL、引擎和 JSON 实现。
 * <p>迁移状态：HTTP契约已从存储实现分离，存量执行器内部仍继续拆分，待统一验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.printing.api;
