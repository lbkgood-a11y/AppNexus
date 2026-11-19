/**
 * 商品主档应用用例验证。
 * <p>职责：检查授权、审批、完整性及拒绝后的无写入顺序。
 * <p>边界与依赖：测试端口替身不替代真实 MySQL 父锁、CAS、回滚和审计验证。
 */
package com.appnexus.masterdata.application.product;
