/**
 * 人员应用边界测试，以输出端口替身验证输入与拒绝路径，不依赖数据库证明纯业务逻辑。
 * <p>允许本上下文内部应用、公开契约及测试框架；禁止引入生产技术适配器。
 * <p>当前状态：测试夹具随端口迁移更新，按要求等待全量迁移后统一执行。
 */
package com.appnexus.platform.organization.internal.application.personnel;
