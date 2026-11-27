package com.appnexus.platform.configuration.internal.application.attachment;

import com.appnexus.platform.configuration.AttachmentFileType;
import com.appnexus.platform.configuration.AttachmentPolicies;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.platform.configuration.port.AttachmentBlobStorage;
import com.appnexus.platform.configuration.port.AttachmentInspection;
import com.appnexus.platform.configuration.port.AttachmentStore;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** 附件用例实现。原件网络写入位于两个独立资料事务之间，过期写入由同请求重试接管。 */
public final class AttachmentService implements AttachmentRecords {
    private final AttachmentStore records;
    private final AttachmentPolicies policies;
    private final AttachmentBlobStorage storage;
    private final AttachmentInspection inspection;
    private final Semaphore uploads = new Semaphore(4);

    /**
     * @param records 执行统一权限和租户范围的资料端口
     * @param policies 提供已授权主体的上传策略
     * @param storage 由部署配置装配的私有存储
     * @param inspection 原件安全检查端口
     */
    public AttachmentService(AttachmentStore records, AttachmentPolicies policies,
                             AttachmentBlobStorage storage, AttachmentInspection inspection) {
        this.records = records;
        this.policies = policies;
        this.storage = storage;
        this.inspection = inspection;
    }

    /** {@inheritDoc} */
    @Override
    public Page list(Owner owner, String businessId, int offset, int limit) {
        validateBusiness(businessId);
        validatePage(offset, limit);
        var page = records.list(owner, businessId, offset, limit);
        return new Page(page.rows().stream().map(AttachmentService::tenantView).toList(), page.total());
    }

    /** {@inheritDoc} */
    @Override
    public Page ledger(Owner owner, Filter filter, int offset, int limit) {
        if (filter.businessId() != null) validateBusiness(filter.businessId());
        if (filter.fileName() != null && filter.fileName().codePointCount(0, filter.fileName().length()) > 255) {
            throw new IllegalArgumentException("附件名称筛选最多 255 个字符");
        }
        if (filter.from() != null && filter.until() != null && !filter.from().isBefore(filter.until())) {
            throw new IllegalArgumentException("附件上传时间范围无效");
        }
        validatePage(offset, limit);
        var result = records.ledger(owner, filter, offset, limit);
        return new Page(result.rows().stream().map(AttachmentService::tenantView).toList(), result.total());
    }

    /** {@inheritDoc} */
    @Override
    public Download download(Owner owner, UUID id) {
        Item item = records.findAvailableInLedger(owner, id);
        return item == null ? null : new Download(item.fileName(), item.sizeBytes(), storage.open(item.storagePath()));
    }

    /** {@inheritDoc} */
    @Override
    public Item upload(Owner owner, String businessId, String fileName, UUID idempotencyKey, InputStream stream) {
        validateBusiness(businessId);
        var type = AttachmentFileType.fromFileName(fileName);
        // 原件与解码中间状态有额外内存开销，同一应用实例最多同时处理四个请求。
        if (!uploads.tryAcquire()) throw new IllegalStateException("附件上传繁忙，请稍后重试");
        try {
            return save(owner, businessId, fileName, idempotencyKey.toString(), stream, type);
        } finally {
            uploads.release();
        }
    }

    private Item save(Owner owner, String businessId, String fileName, String key, InputStream stream,
                      AttachmentFileType type) {
        Item item = records.find(owner, key);
        boolean complete = item != null && "AVAILABLE".equals(item.status());
        long maximum = complete ? type.hardLimitBytes() : policies.uploadRules(owner).stream()
                .filter(rule -> rule.typeKey().equals(type.typeKey()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("附件类型未启用")).maxBytes();
        byte[] data = read(stream, maximum);
        byte[] digest = digest(data);
        if (item != null) {
            if (!item.businessId().equals(businessId) || !item.fileName().equals(fileName)
                    || !MessageDigest.isEqual(records.digest(owner, item.id()), digest)) {
                throw new ConcurrentModificationException("幂等标识已用于其他附件内容");
            }
            if (complete) return tenantView(item);
        }

        inspection.verify(type, data);
        String token = UUID.randomUUID().toString();
        String id = item == null ? UUID.randomUUID().toString() : item.id();
        String path = item == null ? storage.writePath(owner.tenantId(), id) : item.storagePath();
        boolean reserved = item == null
                ? records.reserve(owner, new AttachmentStore.Reservation(id, businessId, fileName, data.length,
                        type.typeKey(), type.mediaType(), digest, path, key, token))
                : records.reclaim(owner, id, token);
        if (!reserved) throw new ConcurrentModificationException("附件正在处理，请重试查询结果");

        try {
            storage.put(path, data, type.mediaType());
        } catch (RuntimeException failure) {
            records.finish(owner, id, token, false);
            throw new IllegalStateException("附件存储暂不可用", failure);
        }
        if (!records.finish(owner, id, token, true)) throw new IllegalStateException("附件记录提交失败");
        return tenantView(records.find(owner, key));
    }

    private static byte[] read(InputStream stream, long maximum) {
        try {
            byte[] data = stream.readNBytes(Math.toIntExact(maximum + 1));
            if (data.length == 0 || data.length > maximum) {
                throw new IllegalArgumentException("附件为空或超过大小限制");
            }
            return data;
        } catch (IOException failure) {
            throw new IllegalArgumentException("附件请求读取失败", failure);
        }
    }

    private static byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("附件摘要算法不可用", failure);
        }
    }

    private static Item tenantView(Item item) {
        return new Item(item.id(), item.tenantId(), item.businessId(), item.fileName(), item.sizeBytes(),
                item.mediaType(), null, item.status(), item.createdBy(), item.createdAt(), item.updatedBy(), item.updatedAt());
    }

    private static void validateBusiness(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip())
                || value.codePointCount(0, value.length()) > 128
                || value.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE)) {
            throw new IllegalArgumentException("业务 ID 必须为 1 至 128 个有效字符");
        }
    }

    private static void validatePage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("分页参数无效");
    }
}
