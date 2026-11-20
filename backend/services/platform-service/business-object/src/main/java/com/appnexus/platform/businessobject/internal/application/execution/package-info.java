/**
 * 职责：对象公共执行协调与专业能力驱动；只依赖公开契约和同上下文事务端口。
 * 边界：禁止依赖框架、访问业务表或按对象类型编写分支。对象授权与记录读取经唯一 Provider.runtime 接入。
 * 当前提供关联解析、版本校验及工作流/打印驱动，审批终态的业务落地仍由业务对象拥有。
 */
package com.appnexus.platform.businessobject.internal.application.execution;
