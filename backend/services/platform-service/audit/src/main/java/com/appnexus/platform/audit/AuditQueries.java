package com.appnexus.platform.audit;
import java.util.*;
import java.time.Instant;
import com.appnexus.platform.tenant.VerifiedTenantContext;
/** 统一受权审计只读公开用例，不返回业务正文、凭据或授权JSON。 */
public interface AuditQueries {
    /** 审计摘要；目标受字段隐藏策略时为 null，traceId 未采集时为 null。 */
    public record Entry(String source,String eventId,String actor,String action,String target,String traceId,Integer status,Instant occurredAt){}
    /** 固定分页契约，items/total 使用完全一致的租户边界。 */
    public record Page(List<Entry> items,long total,int page,int pageSize){}

 /** @param actor 可信平台账号 @param tenant 目标租户可空，null为平台审计 @param page 1至100000 @param size 1至100 @return 原元数据摘要分页 */
 Page platform(UUID actor,UUID tenant,int page,int size);
 /** @param context 可信当前租户及成员，不接受目标租户覆盖 @param page 1至100000 @param size 1至100 @return 原元数据摘要分页 */
 Page tenant(VerifiedTenantContext context,int page,int size);
 /** 稳定业务拒绝，HTTP映射留在API边界。 */
 final class Rejection extends RuntimeException {
  private final int status;
  /** @param status 原HTTP拒绝状态 @param code 原稳定业务码 */
  public Rejection(int status,String code){super(code);this.status=status;}
  /** @return 原HTTP拒绝状态 */
  public int status(){return status;}
 }
}
