/**
 * 进仓累计实收约束。职责边界限于纯业务规则，仅依赖 JDK，禁止依赖其他业务领域、应用、适配器及技术框架。
 * 公开边界为本模块内部。类型供本模块应用编排使用，跨模块通过公开 API。当前仅迁入本包规则类覆盖的业务判断。
 */
package com.appnexus.bulkwarehousing.domain.receiving;
