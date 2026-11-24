package com.appnexus.platform.configuration.port;

import com.appnexus.platform.configuration.AttachmentFileType;

/** 附件内容识别和安全扫描端口，未完成检查时按失败处理。 */
@FunctionalInterface
public interface AttachmentInspection {
    /**
     * @param type 由附件名选择的预期类型
     * @param content 已受大小上限限制的原始内容
     * @throws IllegalArgumentException 内容格式不匹配或明确检出风险
     * @throws IllegalStateException 扫描服务不可用
     */
    void verify(AttachmentFileType type, byte[] content);
}
