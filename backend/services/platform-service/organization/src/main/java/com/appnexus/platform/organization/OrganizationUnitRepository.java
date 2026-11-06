package com.appnexus.platform.organization;
import java.util.*;
/**
 * 组织单元进程内端口；所有读写依赖已验证的租户上下文，由持久化适配器强制行级条件。
 */
public interface OrganizationUnitRepository {
    /** @return 当前租户组织单元，数据库过滤 tenant_id，禁止跨租户投影。 */
    List<OrganizationUnit> findAll();
    /** @param unit 仅含业务 ID 和名称，租户标识不能由调用者传入 */
    void create(OrganizationUnit unit);
    /** @return 当前租户中的目标行存在时返回 true */
    boolean rename(UUID id, String name);
    /** @return 当前租户中的目标行存在时返回 true */
    boolean delete(UUID id);
}
