package com.appnexus.platform.configuration;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
/** 租户非敏感参数公开用例；不是系统密钥或任意运行配置入口。 */
public interface TenantParameters {
    /**
     * 参数定义与当前有效值。
     * @param key 受控参数编码，不接受任意系统配置
     * @param value 当前值，未配置时为明确默认值
     * @param defaultValue 默认值，不表示已持久化
     * @param description 相邻中文参数说明，覆盖用途、单位、默认及影响
     * @param version 未持久化为 -1，首次创建回传 -1；已有从零开始
     */
    public record Parameter(String key,String value,String defaultValue,String description,int version){}

 /** @param context 认证链建立的有效租户成员 @return 固定非敏感参数及明确缺省值 */
 List<Parameter> list(VerifiedTenantContext context);
 /** @param context 可信实际主体 @param key 固定参数编码 @param value 已定义范围的参数值 @param version 首次-1，后续原非负版本 @return CAS更新后参数；失败整笔回滚 */
 Parameter save(VerifiedTenantContext context,String key,String value,int version);
 /** 稳定业务拒绝，HTTP映射由API适配器处理，不暴露数据库细节。 */
 final class Rejection extends RuntimeException {
  private final int status;
  /** @param status 原HTTP状态 @param code 原稳定业务码 */
  public Rejection(int status,String code){super(code);this.status=status;}
  /** @return 原拒绝HTTP状态 */
  public int status(){return status;}
 }
}
