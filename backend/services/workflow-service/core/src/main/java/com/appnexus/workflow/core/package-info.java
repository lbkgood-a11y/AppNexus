/**
 * 工作流受控模型、图校验与无副作用路径预演；仅依赖公共契约与JDK，不引用平台实现、Spring或Flowable。 引擎执行、持久化、身份适配和HTTP属于适配层；业务单据及生效由业务服务拥有。
 * <p>职责：工作流模型、条件、预览与校验等纯核心规则。
 * <p>边界与依赖：仅通过稳定类型和核心端口表达业务；不依赖 Flowable、Spring 或数据库。
 */
package com.appnexus.workflow.core;
