package com.appnexus.platform.tenant;
import java.util.*;
import java.time.Instant;
/** 已有账号邀请公开用例，不创建新账号、不信任未验证邮箱、不恢复历史授权。 */
public interface TenantInvitations {
    /** 公开管理摘要不返回凭证摘要、账号联系方式或权限信息。 */
    public record View(UUID id,UUID tenantId,UUID accountId,String status,Instant expiresAt){}
    /** 随机邀请凭证仅返回一次，必须通过已核实对象的受控渠道传递。 */
    public record Created(View invitation,String token){@Override public String toString(){return "Created[redacted]";}}
    /** 接受结果保留成员 UUID，重新加入不产生第二个身份。 */
    public record Accepted(UUID tenantId,UUID memberId){}

 /** @param actor 可信平台账号 @param tenant 启用租户 @param account 已有有效账号 @return 原24小时凭证，只返回一次、不写日志 */
 Created invite(UUID actor,UUID tenant,UUID account);
 /** @param actor 可信平台账号 @param tenant 固定目标租户 @return 最近100条非敏感邀请摘要 */
 List<View> list(UUID actor,UUID tenant);
 /** @param account 本人可信账号 @param id 邀请UUID @param token 原64位十六进制凭证 @return 复用或新建成员，不恢复旧授权 */
 Accepted accept(UUID account,UUID id,String token);
 /** @param actor 可信平台账号 @param tenant 目标租户 @param id 待接受邀请，撤销不删除历史 */
 void revoke(UUID actor,UUID tenant,UUID id);
 /** 原稳定业务拒绝，由API映射，不泄漏Token或数据库内容。 */
 final class Rejection extends RuntimeException {
  private final int status;
  /** @param status 原拒绝状态 @param code 稳定拒绝码 */
  public Rejection(int status,String code){super(code);this.status=status;}
  /** @return 原HTTP拒绝状态 */
  public int status(){return status;}
 }
}
