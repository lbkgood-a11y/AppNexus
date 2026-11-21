package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印任务专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintJobDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.job", "", "打印任务", "Print jobs", List.of()),
                new DataResource("print.job.artifact", "print.job", "打印产物", "Print artifacts", List.of()),
                new DataResource("print.job.receipt", "print.job", "打印回执", "Print receipts", List.of())), Map.of());
        return new Descriptor("PrintJob", "打印任务", "Print Job", "print", 2, Category.LEDGER, List.of(), List.of(
                operation("PrintJob", "list", "打印任务：查看列表", "View print jobs", "print.job:read",
                        new PageOperation("print/index", "/print/management", "打印任务：查看列表", "View print jobs", "GET", "/api/printing/jobs")),
                operation("PrintJob", "reprint", "重新打印", "Reprint", "print.job:reprint",
                        new PageOperation("print/index", "/print/management", "重新打印", "Reprint", "POST", "/api/printing/jobs/{id}/reprint")),
                operation("PrintJob", "downloadPdf", "打印任务：下载 PDF", "Download print artifact", "print.job:read",
                        new PageOperation("print/index", "/print/management", "打印任务：下载 PDF", "Download print artifact", "GET", "/api/printing/jobs/{id}/pdf")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintJob", "claim", "领取打印任务", "Claim print job", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintJob", "receipt", "回报打印任务结果", "Report print job receipt", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintJob", "uploadPdf", "上传打印产物", "Upload print artifact", "")), access);
    }
}
