package com.appnexus.masterdata.application.masterdata;

import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.masterdata.StatusHistory.Page;
import com.appnexus.masterdata.api.masterdata.StatusHistory.Subject;
import com.appnexus.masterdata.application.RecordTransaction;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 复用对象当前准入查询后读取最小启停历史；查询和权限验证在同一个读事务执行。 */
public final class StatusHistoryQuery {
    private final MasterDataManagement records;
    private final AccessAuthorization access;
    private final RecordTransaction transaction;
    private final StatusHistoryRepository history;

    public StatusHistoryQuery(MasterDataManagement records, AccessAuthorization access,
                              RecordTransaction transaction, StatusHistoryRepository history) {
        this.records = records;
        this.access = access;
        this.transaction = transaction;
        this.history = history;
    }

    /** @param context 服务端可信租户主体
     * @param subject 固定路由归属
     * @param id 主档 UUID
     * @param page 从 1 开始
     * @param size 每页 1 至 100 条
     * @return 授权对象历史，降序分页
     * @throws Rejection 无准入或主档不可见时拒绝
     */
    public Page list(VerifiedTenantContext context, Subject subject, String id, int page, int size) {
        if (context == null) throw denied();
        var actor = AccessAuthorization.Subject.tenant(context.accountId(), context.tenantId(), context.memberId());
        if (!access.hasPermission(actor, subject.resource + ":read")) throw denied();
        UUID record;
        try {
            record = UUID.fromString(id);
            if (!record.toString().equals(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new Rejection(400, "MASTER_DATA_INVALID");
        }
        if (page < 1 || size < 1 || size > 100) throw new Rejection(400, "MASTER_DATA_INVALID");
        return transaction.execute(() -> {
            if (subject.kind != null && records.list(context, subject.kind, "", null, 1, 1, id, "").rows()
                    .stream().noneMatch(row -> id.equals(row.id()))) throw denied();
            // 商品目录各固定子资源的读取范围是当前租户；查询同时绑定租户及固定子资源。
            return history.read(context.tenantId(), subject, record, (long) (page - 1) * size, size);
        }, null);
    }

    private static Rejection denied() {
        return new Rejection(403, "MASTER_DATA_DENIED");
    }
}
