package com.appnexus.masterdata.application.masterdata;

import com.appnexus.masterdata.api.masterdata.StatusHistory.Page;
import com.appnexus.masterdata.api.masterdata.StatusHistory.Subject;
import java.util.UUID;

/** 主数据历史查询端口；仅接受固定对象类型和可信租户，不承担功能授权。 */
public interface StatusHistoryRepository {
    /** @param tenant 可信租户 UUID
     * @param subject 路由决定的固定记录类型
     * @param id 已授权的记录 UUID
     * @param offset 从零开始的分页偏移
     * @param size 每页条数，1 至 100
     * @return 版本倒序的历史及同条件总数
     */
    Page read(UUID tenant, Subject subject, UUID id, long offset, int size);
}
