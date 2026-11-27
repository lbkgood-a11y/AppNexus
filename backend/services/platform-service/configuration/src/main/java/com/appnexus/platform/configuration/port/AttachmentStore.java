package com.appnexus.platform.configuration.port;

import com.appnexus.platform.configuration.AttachmentRecords.Filter;
import com.appnexus.platform.configuration.AttachmentRecords.Item;
import com.appnexus.platform.configuration.AttachmentRecords.Owner;
import com.appnexus.platform.configuration.AttachmentRecords.Page;
import java.util.UUID;

/** 附件专用资料存储端口。实现负责统一 SQL 门禁和各短事务的原子性。 */
public interface AttachmentStore {
    /** 原件写入前的固定身份、摘要与写入令牌。sizeBytes 单位为原始文件字节。 */
    record Reservation(String id, String businessId, String fileName, long sizeBytes, String typeKey,
                       String mediaType, byte[] digest, String path, String idempotencyKey, String token) {}

    /** @return 当前主体的可用资料及同范围总数 */
    Page list(Owner owner, String businessId, int offset, int limit);

    /** @return 当前租户已获台账准入的资料及同范围总数 */
    Page ledger(Owner owner, Filter filter, int offset, int limit);

    /** @return 当前租户台账中按附件 UUID 查询的可用资料，未找到返回空 */
    Item findAvailableInLedger(Owner owner, UUID id);

    /** @return 本人的幂等写入资料，未登记返回空 */
    Item find(Owner owner, String idempotencyKey);

    /** @return 本人已登记附件的内容摘要 */
    byte[] digest(Owner owner, String id);

    /** 原子登记写入。幂等标识竞争返回 false。 */
    boolean reserve(Owner owner, Reservation reservation);

    /** 仅允许相同经校验请求重新取得失败或过期写入的令牌。 */
    boolean reclaim(Owner owner, String id, String token);

    /** 仅持有当前令牌的请求可以完成写入。 */
    boolean finish(Owner owner, String id, String token, boolean available);
}
