package com.appnexus.platform.persistence.identity;

import com.appnexus.platform.tenant.TenantDirectory;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 租户存在性适配；固定平台租户表查询，调用方必须先校验平台管理权限。类保留可代理，以支持宿主持久化异常转换。 */
@Repository
public class MyBatisTenantDirectory implements TenantDirectory {
  private final TenantAccessMapper mapper;
  /** @param mapper 已登记平台范围及固定 SQL 的 Mapper，不接收任意表名 */
  public MyBatisTenantDirectory(TenantAccessMapper mapper){this.mapper=mapper;}
  /** {@inheritDoc} */
  @Override public boolean exists(UUID tenant){return mapper.exists(tenant.toString());}
}
