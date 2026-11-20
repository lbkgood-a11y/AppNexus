package com.appnexus.masterdata.application;
import com.appnexus.masterdata.api.masterdata.MasterData.*;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 主数据查询与聚合关系输出端口；不接收 SQL，COUNT、行范围、字段策略和明细共享当前事务。 */
public interface MasterDataRelations {
  /** 已授权分页与详情查询；page 为1至1000000，size 为1至100，id/scope 只缩小当前租户范围。 */
  Page list(VerifiedTenantContext context,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope);
  /** 读取当前租户地理摘要，不读取账号目录或其他租户。 */
  Object geographyCatalog(VerifiedTenantContext context);
  /** 读取租户地理树；parent 为空读取洲，分页单位为当前层节点。 */
  Object geographyTree(VerifiedTenantContext context,Kind kind,String parent,int page,int size);
  /** 在当前写事务锁定仓储父级并验证启用和不可移动约束；id 新建时为空。 */
  void prepareStorage(VerifiedTenantContext context,Kind kind,String id,Map<String,String> values);
  /** 在当前写事务校验仓储启停，禁止启用孤立节点或停用仍有启用子节点的父级。 */
  void storageStatus(VerifiedTenantContext context,Kind kind,String id,boolean enabled);
  /** 锁定租户地理关系并验证父链；values 接收服务端推导的父级与国别，不信任客户端事实。 */
  void prepareGeography(VerifiedTenantContext context,Kind kind,Input input,Map<String,String> values);
  /** 同事务同步地理后代的国别与审计，失败回滚整个移动。 */
  void synchronizeGeography(VerifiedTenantContext context,Kind kind);
  /** 已授权父对象写入后保存客商明细；null 保留，空集合清空，审计与父写入原子提交。 */
  void savePartnerDetails(VerifiedTenantContext context,String id,Input input);
}
