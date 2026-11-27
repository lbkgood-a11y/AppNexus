package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import static com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 品类模板业务配置的唯一声明。 */
public final class CommodityTemplateDeclaration implements Provider {
    /** {@inheritDoc} */
    @Override public Descriptor descriptor(){
        var list=DedicatedOperations.operation("BulkCommodityTemplate","list","查询品类模板","List commodity templates",READ,page("GET",PATH));
        var save=DedicatedOperations.operation("BulkCommodityTemplate","save","保存品类模板草稿","Save commodity template draft",WRITE,
                page("POST",PATH),page("PUT",PATH+"/{id}"));
        var publish=DedicatedOperations.operation("BulkCommodityTemplate","publish","发布品类模板","Publish commodity template",PUBLISH,
                page("POST",PATH+"/{id}:publish"));
        return new Descriptor(OBJECT,"入库品类模板","Inbound commodity template","bulk-warehousing",1,Category.CONFIGURATION,
                List.of(),List.of(list,save,publish),new DataAccess("TENANT",
                List.of(new DataResource(RESOURCE,"","入库品类模板","Inbound commodity template",List.of())),
                Map.of("list",Set.of("ALL"),"save",Set.of("ALL"),"publish",Set.of("ALL"))));
    }
    private static PageOperation page(String method,String path){
        return new PageOperation("warehousing/inbound-templates","/warehousing/inbound-templates","入库品类模板",
                "Inbound commodity templates",method,path);
    }
}
