/**
 * 人员与人员详情的存储、内容编解码、身份执行桥接及事务装配。
 * <p>职责：人员与人员详情的存储、内容编解码、身份执行桥接及事务装配；当前类型：JacksonPersonnelContent、MyBatisPersonnelRepository、MyBatisPersonRepository、PersonnelCatalogAdapter、PersonnelExecutionAdapter、PersonnelExecutionMapper、PersonnelIdentityMapper、PersonnelMapper、PersonnelStorageValues、PersonRecordMapper、SpringPersonnelTransaction。
 * <p>公开边界与依赖：允许本服务稳定契约、输出端口、MyBatis 与必要的存储技术；供本服务用例及装配接入，禁止提供任意 SQL、读取其他业务私表或自行决定授权。
 * <p>实际依赖与存量差距：本包实际同时包含 Spring 事务、Bean 装配、Jackson 内容转换及人员目录/执行桥接，允许这些现有技术依赖；职责尚未逐包拆开，不宣称仅含纯 MyBatis 存储。
 * <p>迁移状态：本批完成类型落位，原用例依赖仍须继续治理；包迁移不代表完整分层或运行验证通过。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.persistence.personnel;
