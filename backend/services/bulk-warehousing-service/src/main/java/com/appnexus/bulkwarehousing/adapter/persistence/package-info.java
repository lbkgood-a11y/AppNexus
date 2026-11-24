/**
 * 职责为入仓通知签发、查询及历史实物库存总览的 MyBatis 存取和事务。
 * 依赖边界允许本模块存储端口、Spring 事务及 MyBatis，禁止调用其他模块私表或自行决定授权。
 */
package com.appnexus.bulkwarehousing.adapter.persistence;
