package com.appnexus.platform.configuration;
import java.util.*;
import com.appnexus.platform.configuration.port.*;
/** 合作方字典引用公开用例；可信客户端、固定租户和 scope 由统一认证边界建立。 */
public interface PartnerReferences {
 /** 权威机密客户端上下文；authorityVersion 为已验证 Token 的授权版本，不接受请求体指定。 */
 record Context(String client,String tenant,Object authorityVersion) {}
 /** 对外登记输入，不接受内部字典项 UUID、租户或操作者。 */
 record Creation(String dictionaryCode,String itemValue,String referenceKey) {}
 /** 对外引用投影，不暴露内部字典项、租户或审计身份。 */
 record Reference(UUID id,String referenceKey) {}
 /** 稳定业务拒绝，不携带 SQL、Token 或底层异常。 */
 final class Rejection extends RuntimeException {
  private final int status;
  /** @param status HTTP 映射状态 @param code 稳定拒绝码 */
  public Rejection(int status,String code){super(code);this.status=status;}
  /** @return 标准拒绝状态 */
  public int status(){return status;}
 }
 /** @param context 统一认证建立的可信身份 @param key 16–128位幂等键 @param input 固定字典业务值 @return 稳定引用结果；冲突拒绝整笔回滚 */
 Reference create(Context context,String key,Creation input);
 /** @param context 统一认证身份 @param id 对外引用 UUID @return 仅当前客户端和租户自己的引用 */
 Reference get(Context context,UUID id);
 /** 无框架装配入口。 @param records 固定存取输出端口 @param authority 权威客户端资格端口 @param transaction REQUIRED 同连接事务端口 @return 公开引用用例 */
 static PartnerReferences create(PartnerReferenceRepository records,PartnerClientAuthority authority,PartnerReferenceTransaction transaction){return new com.appnexus.platform.configuration.internal.application.dictionary.PartnerReferenceService(records,authority,transaction);}
}
