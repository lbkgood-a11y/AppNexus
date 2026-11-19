package com.appnexus.masterdata.application;

import com.appnexus.masterdata.MasterData.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 主数据记录写入端口；调用方完成授权并开启事务，适配器必须限定可信租户并保持创建审计不变。 */
public interface MasterDataRecords {
  /** 新增固定类型档案，字段取自服务端白名单；审计主体与数据库 UTC 时间在首次 INSERT 写入。 */
  void create(VerifiedTenantContext context, Kind kind, String id, Input input, Map<String,String> values);
  /** 按租户、标识、原编码及版本更新；冲突返回业务拒绝，不推进审计。 */
  void update(VerifiedTenantContext context, Kind kind, String id, Input input, Map<String,String> values);
  /** CAS 启停；调用方必须先验证父子层级及独立启停权限。 */
  void status(VerifiedTenantContext context, Kind kind, String id, Status input);
  /** 设置客商归属，只允许在已验证原记录与目标范围的事务中调用。 */
  void organization(VerifiedTenantContext context, String id, String organizationId);
  /** 读取当前租户原属性，供字典停用项保持不变的业务校验使用。 */
  Map<String,String> previous(VerifiedTenantContext context, Kind kind, String id);
  /** 与正文同事务推进最后修改审计；不覆盖创建信息。 */
  void audit(VerifiedTenantContext context, Kind kind, String id);
}
