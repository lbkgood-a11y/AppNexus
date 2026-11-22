/**
 * 职责为进仓通知、接管、实收和待检库存的 MyBatis 存取及同库事务。
 * 边界允许本模块存储端口、Spring 事务及 MyBatis，禁止调用其他模块私表或决定授权。
 */
package com.appnexus.bulkwarehousing.adapter.persistence;
