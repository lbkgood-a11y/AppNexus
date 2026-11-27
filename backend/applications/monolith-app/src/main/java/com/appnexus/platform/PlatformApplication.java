package com.appnexus.platform;
import com.appnexus.bulkwarehousing.bootstrap.BulkWarehousingConfiguration;
import com.appnexus.masterdata.bootstrap.MasterDataConfiguration;


import com.appnexus.workflow.adapter.flowable.WorkflowEngineConfiguration;
import com.appnexus.printing.bootstrap.PrintConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 平台、工作流与打印服务的组合启动入口；仅此装配层允许引用服务实现。
 * 保留平台扫描范围，通过显式导入装配共享服务，避免扩大扫描引入无关组件。
 */
@SpringBootApplication
// 显式扫描已接入的存储适配器，不能仅依赖启动类包范围而漏掉平台外业务Mapper。
@org.mybatis.spring.annotation.MapperScan(basePackages={"com.appnexus.platform.persistence","com.appnexus.masterdata.adapter.persistence","com.appnexus.bulkwarehousing.adapter.persistence","com.appnexus.printing.adapter.persistence","com.appnexus.leave.adapter.persistence","com.appnexus.workflow.adapter.persistence"},annotationClass=org.apache.ibatis.annotations.Mapper.class)
@Import({PersonnelConfiguration.class, MasterDataConfiguration.class, BulkWarehousingConfiguration.class, WorkflowEngineConfiguration.class, PrintConfiguration.class, com.appnexus.platform.bootstrap.businessobject.BusinessObjectConfiguration.class, com.appnexus.leave.LeavePocConfiguration.class})
public class PlatformApplication {
    /**
     * 启动组合应用，工作流引擎默认关闭，须完成受控数据库迁移后显式启用。
     * @param args Spring Boot 命令行参数；不得通过命令行传递敏感凭据
     */
    public static void main(String[] args) { SpringApplication.run(PlatformApplication.class, args); }
}
