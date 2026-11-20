/**
 * 职责：历史迁移中将固定MyBatis Mapper绑定到迁移工具拥有的连接。
 * <p>边界：依赖JDBC和MyBatis技术API，不依赖Spring、业务表、租户或授权模型；不接收任意SQL。
 * 仅供Flyway历史迁移装配，连接提交、回滚及生命周期继续由迁移主体拥有。
 * <p>当前状态：迁移连接会话适配已建立，业务Mapper和历史校验和验证仍由所属模块负责。
 */
package com.appnexus.persistence.migration;
