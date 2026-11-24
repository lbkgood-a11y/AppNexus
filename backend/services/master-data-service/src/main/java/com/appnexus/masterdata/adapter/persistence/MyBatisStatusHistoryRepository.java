package com.appnexus.masterdata.adapter.persistence;

import com.appnexus.masterdata.api.masterdata.StatusHistory.Page;
import com.appnexus.masterdata.api.masterdata.StatusHistory.Subject;
import com.appnexus.masterdata.application.masterdata.StatusHistoryRepository;
import java.util.UUID;

/** 主数据启停历史的固定 MyBatis 查询适配器。 */
public final class MyBatisStatusHistoryRepository implements StatusHistoryRepository {
    private final MasterStatusHistoryMapper mapper;

    public MyBatisStatusHistoryRepository(MasterStatusHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Page read(UUID tenant, Subject subject, UUID id, long offset, int size) {
        return new Page(mapper.list(tenant.toString(), subject.code, id.toString(), offset, size),
                mapper.count(tenant.toString(), subject.code, id.toString()));
    }
}
