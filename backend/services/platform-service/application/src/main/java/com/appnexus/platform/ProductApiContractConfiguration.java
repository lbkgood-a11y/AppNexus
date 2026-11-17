package com.appnexus.platform;

import com.appnexus.masterdata.product.ProductV1Controller;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 商品SDK方法名只在组合文档层装配；不修改业务operationId，也不增加接口执行权限。 */
@Configuration(proxyBeanMethods=false)
public class ProductApiContractConfiguration {
    /** 每个真实HTTP方法映射唯一OpenAPI名称，结构预览与执行绝不复用同一个operationId。 */
    @Bean GlobalOperationCustomizer productOperationNames(){
        return (operation,handler)->{
            if(handler.getBeanType()!=ProductV1Controller.class)return operation;
            String id=switch(handler.getMethod().getName()){
                case "list" -> "productV1List";
                case "read" -> "productV1Read";
                case "create" -> "productV1CreateDraft";
                case "update" -> "productV1Update";
                case "status" -> "productV1SetStatus";
                case "archive" -> "productV1ArchiveDraft";
                case "restore" -> "productV1RestoreDraft";
                case "changes" -> "productV1ReadChanges";
                case "preview" -> "productV1PreviewStructureMigration";
                case "migrate" -> "productV1MigrateStructure";
                case "options" -> "productV1ReadOptions";
                case "configuration" -> "productV1ReadConfiguration";
                case "configurations" -> "productV1ListConfigurations";
                default -> throw new IllegalStateException("PRODUCT_OPENAPI_OPERATION_UNREGISTERED");
            };
            operation.setOperationId(id);
            return operation;
        };
    }
}
