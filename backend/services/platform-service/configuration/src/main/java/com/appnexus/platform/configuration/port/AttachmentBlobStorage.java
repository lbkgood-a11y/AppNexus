package com.appnexus.platform.configuration.port;

import java.util.UUID;

/** 附件原件私有存储端口。生产适配器由部署配置装配。 */
public interface AttachmentBlobStorage {
    /**
     * 为新记录固定租户及存储目标，原记录重试复用已保存的路径。
     * @param tenantId 服务端认证的租户标识
     * @param attachmentId 应用生成的附件 UUID
     * @return 桶内对象键
     * @throws IllegalStateException 当前租户无可用连接
     */
    String writePath(UUID tenantId, String attachmentId);

    /**
     * 写入已检查的原件。同一幂等请求复用固定对象路径及相同文件内容。
     * @param path 应用生成的桶内对象键
     * @param data 原始附件字节，最多 20 MiB
     * @param contentType 已识别的标准媒体类型
     */
    void put(String path, byte[] data, String contentType);
}
