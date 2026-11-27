/**
 * 客商对象及操作、字段和数据范围的唯一声明。
 * <p>职责：客商对象及操作、字段和数据范围的唯一声明；当前类型：BusinessPartnerDeclaration、BusinessPartnerCommandRuntime。
 * <p>公开边界与依赖：允许对象与能力公开契约、本侧稳定数据和统一权限模型；通过同一 Provider 及运行入口接入，禁止建立平行声明、授权算法或直接访问其他业务私表。
 * <p>迁移状态：对象声明接通类型化命令端口，复用原客商用例、启停历史、权威成员及业务事务。
 */
package com.appnexus.masterdata.adapter.integration.partner;
