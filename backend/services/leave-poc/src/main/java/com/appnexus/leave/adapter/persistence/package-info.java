/**
 * 请假记录固定 SQL、MyBatis 仓储及日期等存储值转换。
 * <p>职责：请假记录固定 SQL、MyBatis 仓储及日期等存储值转换；当前类型：LeaveRecordMapper、LeaveStorageValues、MyBatisLeaveRepository。
 * <p>公开边界与依赖：允许本服务稳定契约、输出端口、MyBatis 与必要的存储技术；供本服务用例及装配接入，禁止提供任意 SQL、读取其他业务私表或自行决定授权。
 * <p>迁移状态：本批完成类型落位，原用例依赖仍须继续治理；包迁移不代表完整分层或运行验证通过。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.leave.adapter.persistence;
