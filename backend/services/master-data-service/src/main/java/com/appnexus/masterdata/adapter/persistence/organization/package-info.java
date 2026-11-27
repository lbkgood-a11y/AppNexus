/**
 * 职责：组织树、组织单元、岗位任职及人员目录的固定持久化映射和现有事务协调。
 * <p>边界：通过组织公开契约服务调用者，使用公共权限计划、字段保护和受控 SQL；
 * 禁止直接授予角色或绕开平台授权。该包保留迁入前的混合适配结构，后续拆分用例与仓储。
 */
package com.appnexus.masterdata.adapter.persistence.organization;
