package com.appnexus.platform.configuration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 附件上传安全策略用例，调用方必须先执行对应的平台管理或附件准入。 */
public interface AttachmentPolicies {
    /** 规则快照。maxBytes 和 hardLimitBytes 均为原始文件字节数，version 用于防止并发覆盖。 */
    record Rule(String typeKey, boolean enabled, long maxBytes, long version,
                long hardLimitBytes, List<String> extensions, Audit audit) {}

    /** 平台配置审计快照。未知账号为空，时间来自数据库。 */
    record Audit(String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {}

    /** @return 全部附件规则，包含停用项，供已授权的平台管理员维护 */
    List<Rule> rules(UUID actor);

    /** @return 授权上传主体可以使用的规则，仅包含启用项 */
    List<Rule> uploadRules(AttachmentRecords.Owner owner);

    /**
     * 更新现有规则，禁止添加目录以外的格式。
     * @param actor 通过统一管理准入的可信操作账号
     * @param typeKey 已发布的格式键
     * @param enabled 是否接受该类型的新上传
     * @param maxBytes 单文件字节上限，至少为 1 且不得超过格式硬上限
     * @param version 读取时的规则版本
     * @return 保存后的规则
     * @throws java.util.ConcurrentModificationException 规则已被其他操作修改
     */
    Rule update(UUID actor, String typeKey, boolean enabled, long maxBytes, long version);
}
