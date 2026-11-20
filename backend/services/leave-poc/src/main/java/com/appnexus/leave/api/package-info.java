/**
 * 请假公开用例、稳定输入输出及提交操作契约。
 * <p>职责：请假公开用例、稳定输入输出及提交操作契约；当前类型：LeaveData、LeaveManagement、LeaveOperations。
 * <p>公开边界与依赖：允许 JDK 与稳定公开业务契约；作为本服务对外调用边界，不暴露技术存储或内部实现，禁止依赖 Spring、SQL、引擎和 JSON 实现。
 * <p>迁移状态：HTTP边界分离，内部混合实现仍须继续迁移，未验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.leave.api;
