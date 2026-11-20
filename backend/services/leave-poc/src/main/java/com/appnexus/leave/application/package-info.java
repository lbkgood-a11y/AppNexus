/**
 * 请假提交、审批结果协调及记录与事务输出端口。
 * <p>职责：请假提交、审批结果协调及记录与事务输出端口；当前类型：LeavePocService、LeaveRepository、LeaveTransaction。
 * <p>公开边界与依赖：允许 JDK、所属领域规则、稳定公开契约及明确输出端口；仅由本上下文或服务装配访问内部用例，禁止依赖 Spring、SQL、JSON、引擎实现或具体技术适配器。
 * <p>迁移状态：事务、引擎、内容和存储通过显式端口连接；固定结果投影及参数契约仍需最终逐调用核验，未执行统一运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.leave.application;
