package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.CoilTitleTransfer.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 仓储货权转出转入的固定存储端口，参与现有库存事务。 */
public interface CoilTitleStore {
  /** 原结果及请求摘要，用于幂等重放。 */
  record Posted(Transfer result,String digest) {}

  /** 读取已提交的过户。 */
  Posted transfer(VerifiedTenantContext c,String id);
  /** 取得可和签发提货授权比较的新租户货权代次。 */
  long nextRevision(VerifiedTenantContext c);
  /** 同事务追加出入双分录并更新库存身份指向的活动货权。 */
  void transfer(VerifiedTenantContext c,Command command,Transfer result,String digest);
}
