/**
 * 职责与边界。
 * 平台配置域附件用例，负责资料可见性与上传状态编排。
 * 只依赖平台配置公开契约和输出端口，禁止依赖 SQL、HTTP、对象存储 SDK 或其他业务上下文。
 * 通过配置域公开装配方法接入；内部用例不作为外部业务 API。
 */
package com.appnexus.platform.configuration.internal.application.attachment;
