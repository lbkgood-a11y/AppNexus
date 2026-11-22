package com.appnexus.platform.organization.port.personnel;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.organization.PersonData.Input;
/** 人员固定存取输出端口；仅允许平台持久化适配器实现，租户、授权及完整事务由调用用例约束。 */
public interface PersonRepository {
 
 
 /** @param context 可信主体 @param ids 目标人员 UUID @return 权限范围内的名称 */
 Map<String,String> names(VerifiedTenantContext context,List<String> ids);
 /** 同一个可信数据范围覆盖人员分页及总数。 */
 record PersonPage(List<Map<String,Object>> rows,long total){}
 /**
  * @param context 当前可信租户、账号及经验证人员
  * @param keyword 字面查询关键字，空字符串不限制
  * @param enabled 可空启停条件
  * @param size 每页条数，1–100
  * @param offset 从零开始的结果偏移
  * @return 已应用相同人员范围的总数与页数据
  */
 PersonPage page(VerifiedTenantContext context,String keyword,Boolean enabled,int size,int offset);

 /** @param context 可信创建主体 @param id 新档案 UUID @param input 业务字段 @param values 已校验字段 @return 新增行数 */
 int create(VerifiedTenantContext context,String id,Input input,Map<String,String> values);

  /**
   * 在同一写事务内锁定当前操作获准的人员档案，拒绝缺失上下文或越权，防止先读取原字段。
   * @param context 可信租户与本次验证后的人员身份
   * @param id 已校验人员档案 UUID
   * @param action 固定 update 或 set_status
   */
  void lockForWrite(VerifiedTenantContext context,String id,String action);

  int update( String tenant, String id, Input input, Map<String,String> values);
 
  int status( String tenant, String id, boolean enabled, long version);
 
  List<Map<String,String>> previous( String tenant, String id);
 
  int audit( String tenant, String id, String actor);
}
