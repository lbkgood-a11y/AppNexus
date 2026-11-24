package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.contracts.businessobject.BusinessObjects.PageOperation;

/** 单据专用页面与已有 HTTP 操作的关联。 */
enum InboundDocumentPageOperations {
    QUALITY("quality", "质检单", "Quality orders"),
    METERING("metering", "计量单（磅单）", "Metering orders"),
    RECEIPT("receipt", "入库单", "Receipt orders");

    private final String key;
    private final String zh;
    private final String en;

    InboundDocumentPageOperations(String kind, String zh, String en) {
        this.key = "warehousing/inbound-" + kind + "-orders";
        this.zh = zh;
        this.en = en;
    }

    /** @param method HTTP 方法 @param endpoint 正式接口模板 @return 单据页面操作声明 */
    PageOperation entry(String method, String endpoint) {
        return new PageOperation(key, "/" + key, zh, en, method, endpoint);
    }
}
