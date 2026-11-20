/**
 * 职责与边界：平台对象能力技术实现或组合验证；通过公开用例与 port 连接，禁止跨上下文读表及导入 internal。
 * <p>固定查询已逐步迁移到 MyBatis Mapper；其余 JDBC 存量不视为完成迁移。
 * <p>当前状态：对象能力已归入平台上下文；本包说明不代表其他存量模块完成迁移。
 */
package com.appnexus.platform.persistence.businessobject.configuration;
