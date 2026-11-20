package com.appnexus.platform;
import com.appnexus.masterdata.adapter.web.product.ProductUnitConversionController;
import com.appnexus.masterdata.adapter.web.product.ProductStructureController;
import com.appnexus.masterdata.adapter.web.product.ProductImageController;
import com.appnexus.masterdata.adapter.web.product.ProductController;
import com.appnexus.masterdata.adapter.web.product.ProductApprovalController;

import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 商品SDK方法名只在组合文档层装配；不修改业务operationId，也不增加接口执行权限。 */
@Configuration(proxyBeanMethods=false)
public class ProductApiContractConfiguration {
    /** 每个真实HTTP方法映射唯一OpenAPI名称，结构预览与执行绝不复用同一个operationId。 */
    @Bean GlobalOperationCustomizer productOperationNames(){
        return (operation,handler)->{
            var extension=java.util.Map.<Class<?>,String>of(ProductImageController.class,"productImage",ProductUnitConversionController.class,"productUnitConversion",ProductStructureController.class,"productStructure",ProductApprovalController.class,"productApproval");
            if(extension.containsKey(handler.getBeanType())){String method=handler.getMethod().getName();operation.setOperationId(extension.get(handler.getBeanType())+Character.toUpperCase(method.charAt(0))+method.substring(1));return operation;}
            if(handler.getBeanType()!=ProductController.class)return operation;
            String id=switch(handler.getMethod().getName()){
                case "list" -> "productList";
                case "read" -> "productRead";
                case "create" -> "productCreateDraft";
                case "update" -> "productUpdate";
                case "status" -> "productSetStatus";
                case "archive" -> "productArchiveDraft";
                case "restore" -> "productRestoreDraft";
                case "changes" -> "productReadChanges";
                case "preview" -> "productPreviewStructureMigration";
                case "migrate" -> "productMigrateStructure";
                case "options" -> "productReadOptions";
                case "configuration" -> "productReadConfiguration";
                case "stage" -> "productStageLegacy";
                case "reconcile" -> "productReconcileLegacy";
                case "reviewLegacy" -> "productReviewLegacySpecification";
                case "configurations" -> "productListConfigurations";
                default -> throw new IllegalStateException("PRODUCT_OPENAPI_OPERATION_UNREGISTERED");
            };
            operation.setOperationId(id);
            return operation;
        };
    }
}
