/**
 * 业务用例协调及本模块输出端口。
 * <p>允许JDK、纯领域模型和稳定公开业务契约；禁止Spring、Flowable、JDBC、MyBatis、JSON实现和具体技术适配器。
 * <p>迁移状态：事务、引擎、内容和存储通过显式端口连接；固定结果投影及参数契约仍需最终逐调用核验，未执行统一运行验证。
 */
package com.appnexus.leave.application;
