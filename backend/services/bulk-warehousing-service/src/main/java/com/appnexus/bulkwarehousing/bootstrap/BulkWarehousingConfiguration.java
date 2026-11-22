package com.appnexus.bulkwarehousing.bootstrap;

import com.appnexus.bulkwarehousing.adapter.integration.MasterDataInboundReceiptReferences;
import com.appnexus.bulkwarehousing.adapter.integration.InboundReceivingDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.InboundReceivingMapper;
import com.appnexus.bulkwarehousing.adapter.web.InboundReceivingController;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.bulkwarehousing.api.InboundDisposition;
import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisInboundReceivingStore;
import com.appnexus.bulkwarehousing.application.InboundReceivingService;
import com.appnexus.contracts.businessobject.BusinessObjects;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.application.ResolveInboundReceiptReferences;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.product.ProductStructureManagement;
import com.appnexus.masterdata.api.product.UnitConversions;
import com.appnexus.bulkwarehousing.api.OutboundOperations;
import com.appnexus.bulkwarehousing.api.OutboundStock;
import com.appnexus.bulkwarehousing.application.OutboundStockService;
import com.appnexus.bulkwarehousing.adapter.integration.OutboundStockDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.OutboundStockMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisOutboundStockStore;
import com.appnexus.bulkwarehousing.adapter.web.OutboundStockController;
import com.appnexus.bulkwarehousing.api.CoilOutbound;
import com.appnexus.bulkwarehousing.application.CoilOutboundService;
import com.appnexus.bulkwarehousing.adapter.integration.CoilOutboundDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.CoilOutboundMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisCoilOutboundStore;
import com.appnexus.masterdata.api.product.ProductCatalogManagement;
import com.appnexus.bulkwarehousing.api.CoilTitleTransfer;
import com.appnexus.bulkwarehousing.application.CoilTitleTransferService;
import com.appnexus.bulkwarehousing.adapter.integration.CoilTitleDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.CoilTitleMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisCoilTitleStore;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.bulkwarehousing.application.WarehouseBalanceService;
import com.appnexus.bulkwarehousing.adapter.integration.WarehouseStockBalanceDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.WarehouseBalanceMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisWarehouseBalanceStore;
import com.appnexus.masterdata.api.product.ProductManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 大宗仓储的进程内装配，复用同一组合应用中的主数据服务。 */
@Configuration(proxyBeanMethods = false)
public class BulkWarehousingConfiguration {
  /** @param stock 正式库存用例 @return 第一方出仓库存入口 */
  @Bean public OutboundStockController outboundStockController(OutboundStock stock,CoilOutbound coils,CoilTitleTransfer titles,WarehouseBalances balances) {
    return new OutboundStockController(stock,coils,titles,balances);
  }

  /** @return 出仓库存唯一对象声明 */
  @Bean public BusinessObjects.Provider outboundStockDeclaration() {
    return new OutboundStockDeclaration(OutboundOperations.STOCK,"出仓库存","Outbound stock");
  }

  /** @return 库存限制唯一对象声明 */
  @Bean public BusinessObjects.Provider stockRestrictionDeclaration() {
    return new OutboundStockDeclaration(OutboundOperations.RESTRICTION,"库存限制","Stock restriction");
  }

  /** @return 整卷出仓对象声明 */
  @Bean public BusinessObjects.Provider coilOutboundDeclaration() {
    return new CoilOutboundDeclaration();
  }

  /** @param inbound 共用库存锁 @param mapper 出仓 SQL @param coils 库内栅栏 @param manager 数据源事务
   * @return 共用的库存存储及事务 */
  @Bean public MyBatisOutboundStockStore outboundStockStore(InboundReceivingMapper inbound,OutboundStockMapper mapper,
      CoilOutboundMapper coils,PlatformTransactionManager manager) {
    return new MyBatisOutboundStockStore(inbound,mapper,coils,manager);
  }

  /** @param store 实物存储 @param source 主数据公开读取 @param access 统一权限计划 @return 公共库存门禁 */
  @Bean public OutboundStockService outboundStock(MyBatisOutboundStockStore store,
      MasterDataInboundReceiptReferences source,AccessPlanning access,com.appnexus.platform.authorization.ScopedWriteGate writes) {
    return new OutboundStockService(store,source,access,writes);
  }

  /** @param mapper 额度持久化 @param store 共用库存事务 @param gate 库存门禁
   * @param source 主数据公开读取 @param access 统一权限计划 @return 整卷出仓用例 */
  @Bean public CoilOutboundService coilOutbound(CoilOutboundMapper mapper,CoilTitleMapper titles,MyBatisOutboundStockStore store,
      OutboundStockService gate,MasterDataInboundReceiptReferences source,AccessPlanning access,
      com.appnexus.platform.authorization.ScopedWriteGate writes) {
    return new CoilOutboundService(new MyBatisCoilOutboundStore(mapper,titles),store,gate,source,access,writes);
  }

  /** @return 过户唯一对象声明 */
  @Bean public BusinessObjects.Provider coilTitleDeclaration() { return new CoilTitleDeclaration(); }

  /** @param mapper 货权存储 @param outbound 出仓仓级锁 @param stock 库存事务 @param gate 货权事实
   * @param source 原主数据入口 @param access 统一权限 @return 整卷过户业务 */
  @Bean public CoilTitleTransferService coilTitleTransfer(CoilTitleMapper mapper,CoilOutboundMapper outbound,
      MyBatisOutboundStockStore stock,OutboundStockService gate,MasterDataInboundReceiptReferences source,
      AccessPlanning access,com.appnexus.platform.authorization.ScopedWriteGate writes) {
    return new CoilTitleTransferService(new MyBatisCoilTitleStore(mapper),stock,
        new MyBatisCoilOutboundStore(outbound,mapper),gate,source,access,writes);
  }

  /** @return 在库货权余额资源唯一声明 */
  @Bean public BusinessObjects.Provider warehouseStockBalanceDeclaration() { return new WarehouseStockBalanceDeclaration(); }

  /** @param mapper 两类余额 SQL @param stock 库存身份锁 @param source 复用主数据范围
   * @param access 统一权限 @return 余额及追溯查询 */
  @Bean public WarehouseBalanceService warehouseBalances(WarehouseBalanceMapper mapper,MyBatisOutboundStockStore stock,
      MasterDataInboundReceiptReferences source,AccessPlanning access,com.appnexus.platform.authorization.ScopedWriteGate operations) {
    return new WarehouseBalanceService(new MyBatisWarehouseBalanceStore(mapper),stock,source,access,operations);
  }

  /** @param receiving 受控入仓用例 @return 第一方命令控制器 */
  @Bean public InboundReceivingController inboundReceivingController(InboundReceiving receiving, InboundDisposition disposition) {
    return new InboundReceivingController(receiving,disposition);
  }

  /** @return 入仓履约唯一对象声明 */
  @Bean public BusinessObjects.Provider inboundReceivingDeclaration() {
    return new InboundReceivingDeclaration(InboundOperations.OBJECT,"入仓履约","Inbound fulfillment");
  }

  /** @return 独立货权登记对象声明 */
  @Bean public BusinessObjects.Provider inboundTitleDeclaration() {
    return new InboundReceivingDeclaration(InboundOperations.REGISTER_TITLE.object(),"入仓货权登记","Inbound title registration");
  }

  /** @return 独立质量放行对象声明 */
  @Bean public BusinessObjects.Provider inboundQualityDeclaration() {
    return new InboundReceivingDeclaration(InboundOperations.RELEASE_QUALITY.object(),"入仓质量放行","Inbound quality release");
  }

  /**
   * @param mapper 仓储固定 SQL
   * @param manager 同一数据源事务管理器
   * @param source 主数据集成适配器
   * @param references 已验证的进仓引用入口
   * @param access 平台统一权限计划
   * @return 入仓签发、接管和实收用例
   */
  @Bean public InboundReceivingService inboundReceiving(InboundReceivingMapper mapper, PlatformTransactionManager manager,
      MasterDataInboundReceiptReferences source, InboundReceiptReferences references, AccessPlanning access,
      com.appnexus.platform.authorization.ScopedWriteGate writes) {
    return new InboundReceivingService(new MyBatisInboundReceivingStore(mapper,manager),source,references,access,writes);
  }

  /** @return 受治理的主数据出站适配器 */
  @Bean public MasterDataInboundReceiptReferences inboundMasterData(MasterDataManagement masterData,
      ProductStructureManagement products,UnitConversions conversions,ProductCatalogManagement catalog,ProductManagement productRead) {
    return new MasterDataInboundReceiptReferences(masterData,products,conversions,catalog,productRead);
  }

  /**
   * @param source 主数据集成适配器 主数据授权查询入口
   * @param transactions 组合应用共用的数据库事务管理器
   * @return 事务内核验引用的只读入口，调用方已开启事务时加入其事务
   */
  @Bean
  public InboundReceiptReferences inboundReceiptReferences(MasterDataInboundReceiptReferences source,
      PlatformTransactionManager transactions) {
    var useCase = new ResolveInboundReceiptReferences(source);
    var read = new TransactionTemplate(transactions);
    read.setReadOnly(true);
    return (context, request) -> read.execute(status -> useCase.resolve(context, request));
  }
}
