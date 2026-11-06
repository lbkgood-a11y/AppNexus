package com.appnexus.platform.audit;
import java.util.*;
/** 平台级账号安全日志端口；查询必须带服务端认证主体，禁止无条件跨账号查询。 */
public interface SecurityLogRepository {
    /**
     * 追加成功操作，不提供更新和删除入口。
     * @param accountId 已验证的不可变账号 UUID
     * @param action 已实现的稳定事件类型
     */
    void append(UUID accountId, SecurityLog.Action action);
    /**
     * 查询本人记录，按记录标识倒序。
     * @param accountId 服务端会话主体，不能来自客户端参数
     * @param page 从 1 开始的页码
     * @param size 每页 1 至 50 条
     * @return 当前页和账号内记录总数
     */
    Page find(UUID accountId, int page, int size);
    /** 有界分页结果，列表只包含安全展示字段。 */
    record Page(List<SecurityLog> items, long total) {}
}
