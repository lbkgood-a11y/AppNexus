/**
 * 职责：平台对象能力的 Spring 组合装配，连接公开用例、集中适配器及可选能力。
 * <p>边界：允许公开用例和具体技术装配入口，禁止导入对象 internal 或承担业务决策。
 * <p>当前状态：生产组合唯一 Spring 装配；原独立工程装配已迁入此处。
 */
package com.appnexus.platform.bootstrap.businessobject;
