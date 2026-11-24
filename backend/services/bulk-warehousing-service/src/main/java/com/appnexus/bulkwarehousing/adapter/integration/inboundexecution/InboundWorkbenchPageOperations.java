package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.contracts.businessobject.BusinessObjects.PageOperation;

/** 真实工作台页面与各对象独立接口之间的固定交互关联。 */
final class InboundWorkbenchPageOperations {
    private InboundWorkbenchPageOperations() {}

    /** @param method 真实端点 HTTP 方法 @param apiPath 已验证路由 @return 仅供当前已有页面的操作映射 */
    static PageOperation entry(String method,String apiPath) {
        return new PageOperation("warehousing/inbound-workbench","/warehousing/inbound-workbench",
                "入库工作台","Inbound workbench",method,apiPath);
    }
}
