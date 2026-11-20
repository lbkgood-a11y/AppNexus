/**
 * 人员及人员详情的授权、领域校验和输出端口协调。
 * <p>职责：人员及人员详情的授权、领域校验和输出端口协调；当前类型：PersonDataService、PersonnelService。
 * <p>公开边界与依赖：允许 JDK、所属领域规则、稳定公开契约及明确输出端口；仅由本上下文或服务装配访问内部用例，禁止依赖 Spring、SQL、JSON、引擎实现或具体技术适配器。
 * <p>迁移状态：人员边界已拆分，编译、装配及业务安全验证等待全量迁移后统一执行。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.organization.internal.application.personnel;
