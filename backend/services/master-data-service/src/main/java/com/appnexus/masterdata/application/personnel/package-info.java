/**
 * 人员及人员详情的授权、领域校验和输出端口协调。
 * <p>职责：人员及人员详情的授权、领域校验和输出端口协调；当前类型：PersonDataService、PersonnelService。
 * <p>公开边界与依赖：允许 JDK、所属领域规则、稳定公开契约及明确输出端口；仅由本上下文或服务装配访问内部用例，禁止依赖 Spring、SQL、JSON、引擎实现或具体技术适配器。
 * <p>迁移状态：人员档案与明细用例归主数据；由宿主经 PersonnelUseCases 装配，组织目录、人员身份及存储契约仍由平台提供。
 */
package com.appnexus.masterdata.application.personnel;
