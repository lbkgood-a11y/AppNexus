package com.appnexus.contracts.operation;

import java.time.Instant;

/**
 * 只读操作预览凭据公开端口；签名不授予业务权限，执行端必须重新鉴权和校验源版本。
 * 使用与OAuth/JWT不同的协议域，不能把预览凭据作为认证Token；binding仅允许摘要和作用域标识，不存敏感正文。
 */
public interface OperationPreviewProof {
    /** 不透明签名凭据及UTC到期点，不得写日志、URL或浏览器持久存储。 */
    record Proof(String token, Instant expiresAt) {}
    /**
     * @param binding 业务规范化载荷与可信主体作用域的SHA-256摘要，64位小写十六进制
     * @return 15分钟有效的无持久化签名；有效期仅适用于预览，不保证执行成功
     */
    Proof issue(String binding);
    /**
     * @param token issue返回的不透明短期凭据
     * @param binding 执行端重新计算的完整作用域/载荷/准入摘要
     * @return 签名、作用域、载荷及到期校验均通过才为true；格式不合法失败关闭
     */
    boolean verify(String token,String binding);
}
