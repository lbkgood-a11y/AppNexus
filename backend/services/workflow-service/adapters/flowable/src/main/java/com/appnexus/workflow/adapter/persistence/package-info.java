/**
 * 工作流记录固定 MyBatis SQL 与记录输出端口实现。
 * <p>职责：工作流记录固定 MyBatis SQL 与记录输出端口实现；当前类型：MyBatisWorkflowRecords、WorkflowRecordMapper。
 * <p>公开边界与依赖：允许本服务稳定契约、输出端口、MyBatis 与必要的存储技术；供本服务用例及装配接入，禁止提供任意 SQL、读取其他业务私表或自行决定授权。
 * <p>迁移状态：运行协调、引擎、内容、事务与存储已分离；固定存储参数及投影仍在细化，未执行统一编译或运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.workflow.adapter.persistence;
