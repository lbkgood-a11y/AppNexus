package com.appnexus.printing.bootstrap;
import com.appnexus.printing.application.PrintRuntimeService;
import com.appnexus.printing.application.PrintSceneService;
import com.appnexus.printing.application.PrintOutputService;
import com.appnexus.printing.adapter.persistence.*;
import com.appnexus.printing.adapter.web.PrintController;
import com.appnexus.printing.adapter.persistence.PrintRuntimeMapper;
import com.appnexus.printing.adapter.persistence.PrintReceiptMapper;
import com.appnexus.printing.adapter.persistence.PrintOutputsMapper;
import com.appnexus.printing.adapter.persistence.PrintFlowMapper;
import com.appnexus.printing.adapter.persistence.BusinessPrintingMapper;

import com.appnexus.contracts.printing.PrintPlatform;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;


/** 组合应用打印装配；HTTP 和 JDBC 使用宿主认证、事务及受控 Flyway 迁移。 */
@Configuration(proxyBeanMethods=false)
@org.springframework.context.annotation.Import({com.appnexus.printing.adapter.web.PrintDataContractDeclaration.class,
    com.appnexus.printing.adapter.web.PrintDataContractCommandRuntime.class,
    com.appnexus.printing.adapter.web.PrintDeviceDeclaration.class,
    com.appnexus.printing.adapter.web.PrintDocumentDeclaration.class,
    com.appnexus.printing.adapter.web.PrintDocumentCommandRuntime.class,
    com.appnexus.printing.adapter.web.PrintJobDeclaration.class,
    com.appnexus.printing.adapter.web.PrintJobCommandRuntime.class,
    com.appnexus.printing.adapter.web.PrintOutputDeclaration.class,
    com.appnexus.printing.adapter.web.PrintOutputCommandRuntime.class,
    com.appnexus.printing.adapter.web.PrintSceneDeclaration.class,
    com.appnexus.printing.adapter.web.PrintSceneCommandRuntime.class,
    com.appnexus.printing.adapter.web.PrintTemplateDeclaration.class})
public class PrintConfiguration {
  /** 单据与业务模块只引用打印公开契约；模板和输出状态仍由打印服务拥有。 */
  @Bean public com.appnexus.contracts.printing.BusinessPrinting businessPrinting(com.appnexus.printing.application.BusinessPrintQueries queries,com.appnexus.printing.api.PrintOutputManagement outputs,PrintPlatform platform){return new com.appnexus.printing.application.BusinessPrintingService(queries,outputs,platform);}
  /** @return 打印运行时；缺失平台权限桥接时应用启动失败，禁止降级为匿名打印。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintRuntimeService printRuntime(org.springframework.transaction.PlatformTransactionManager manager,PrintPlatform platform,PrintReceiptMapper receiptMapper,PrintRuntimeMapper statements){return new PrintRuntimeService(platform,new com.appnexus.printing.adapter.codec.JacksonPrintRuntimeContent(),new com.appnexus.printing.application.PrintReceiptService(new MyBatisPrintReceiptStore(receiptMapper,tools.jackson.databind.json.JsonMapper.builder().build()),platform),new MyBatisPrintRuntimeRepository(statements),new com.appnexus.printing.adapter.transaction.SpringPrintTransaction(manager));}

  /** @return 租户打印 HTTP 入口；由现有安全过滤链保护。 */
  @Bean public PrintController printController(PrintRuntimeService runtime,PrintSceneService flow,PrintOutputService outputs){return new PrintController(runtime,flow,outputs);}
  /** 统一输出编排复用宿主事务和可信身份，外部 API 不建立第二套认证。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintOutputService printOutputs(org.springframework.transaction.PlatformTransactionManager manager,PrintPlatform platform,PrintRuntimeService runtime,PrintSceneService flow,PrintOutputsMapper statements){return new PrintOutputService(platform,runtime,flow,new com.appnexus.printing.adapter.codec.JacksonPrintPayloadContent(),new MyBatisPrintOutputRepository(statements),new com.appnexus.printing.adapter.transaction.SpringPrintTransaction(manager));}

  /** @return 配置式场景与手工单据事务服务，复用宿主数据库及身份权限。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintSceneService printFlow(org.springframework.transaction.PlatformTransactionManager manager,PrintPlatform platform,PrintRuntimeService runtime,java.util.List<com.appnexus.contracts.printing.PrintDataContractProvider> providers,PrintFlowMapper statements){var flow=new PrintSceneService(platform,runtime,new com.appnexus.printing.adapter.codec.JacksonPrintSceneContent(),new MyBatisPrintSceneRepository(statements),new com.appnexus.printing.adapter.transaction.SpringPrintTransaction(manager));flow.businessContracts(providers);return flow;}
  /** @param mapper 固定场景、渲染事实与历史SQL @return 业务打印查询输出端口 */
  @Bean public com.appnexus.printing.application.BusinessPrintQueries businessPrintQueries(BusinessPrintingMapper mapper){return new MyBatisBusinessPrintQueries(mapper);}
}
