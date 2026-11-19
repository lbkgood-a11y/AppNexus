/**
 * 职责与边界：对象配置及编码的输出端口与稳定快照，仅依赖 JDK 和公开契约。
 * <p>允许平台 persistence 适配器实现存储、事务及编解码端口；CodingObjectAccess
 * 由同上下文应用协调实现。禁止依赖 internal、Spring、JDBC 或其他技术实现。
 * <p>当前状态：对象能力已归入平台上下文；本包说明不代表其他存量模块完成迁移。
 */
package com.appnexus.platform.businessobject.port;
