package com.appnexus.masterdata.application;
import com.appnexus.masterdata.api.masterdata.MasterData.*;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 主数据查询与聚合关系输出端口；不接收 SQL，COUNT、行范围、字段策略和明细共享当前事务。 */
public interface MasterDataRelations {
  /** 已授权分页与详情查询；page 为1至1000000，size 为1至100，id/scope 只缩小当前租户范围。 */
  Page list(VerifiedTenantContext context,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope);
  /** 按独立登录选仓权限和当前租户读取启用仓库摘要。 */
  java.util.List<com.appnexus.masterdata.api.masterdata.MasterDataManagement.LoginWarehouse> loginWarehouses(
      VerifiedTenantContext context, java.util.UUID warehouseId);
  /** 读取当前租户地理摘要，不读取账号目录或其他租户。 */
  Object geographyCatalog(VerifiedTenantContext context);
  /** 读取租户地理树；parent 为空读取洲，分页单位为当前层节点。 */
  Object geographyTree(VerifiedTenantContext context,Kind kind,String parent,int page,int size);
  /**
   * 在写事务中按对应操作范围锁定仓库或所属仓库下的目标记录。
   * @param context 可信租户及成员
   * @param kind 固定主数据类型；非仓储类型不适用
   * @param id 待锁定的现有记录 UUID
   * @param action 固定业务操作 update 或 set_status
   */
  void requireStorageWrite(VerifiedTenantContext context,Kind kind,String id,String action);
  /**
   * @param context 可信租户与身份
   * @param id 现有仓库或服务端为新增预分配的 UUID
   * @param organization 固定目标组织 UUID；更新空值保留原归属，新增空值仅兼容显式全部策略
   * @param creating 是否新增；修改必须先取得原记录的范围锁
   */
  void warehouseTarget(VerifiedTenantContext context,String id,String organization,boolean creating);
  /** @param context 已验证主体 @param action 只允许 create 或 update @return 启用且在操作范围内的候选 */
  Object warehouseOrganizations(VerifiedTenantContext context,String action);
  /** 在当前写事务锁定仓储父级并验证启用和不可移动约束；id 为服务器生成或已锁定的档案 UUID。 */
  void prepareStorage(VerifiedTenantContext context,Kind kind,String id,Map<String,String> values,boolean creating);
  /** 在当前写事务校验仓储启停，禁止启用孤立节点或停用仍有启用子节点的父级。 */
  void storageStatus(VerifiedTenantContext context,Kind kind,String id,boolean enabled);
  /** 锁定租户地理关系并验证父链；values 接收服务端推导的父级与国别，不信任客户端事实。 */
  void prepareGeography(VerifiedTenantContext context,Kind kind,Input input,Map<String,String> values);
  /** 同事务同步地理后代的国别与审计，失败回滚整个移动。 */
  void synchronizeGeography(VerifiedTenantContext context,Kind kind);
  /** 已授权父对象写入后保存客商明细；null 保留，空集合清空，审计与父写入原子提交。 */
  void savePartnerDetails(VerifiedTenantContext context,String id,Input input);
}
