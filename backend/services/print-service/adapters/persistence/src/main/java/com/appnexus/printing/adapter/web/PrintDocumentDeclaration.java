package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印单据专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintDocumentDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.document", "", "打印单据", "Print documents", List.of())), Map.of());
        return new Descriptor("PrintDocument", "打印单据", "Print Document", "print", 2, Category.TRANSACTION, List.of(), List.of(
                operation("PrintDocument", "create", "打印单据：新增", "Create print document", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印单据：新增", "Create print document", "POST", "/api/printing/documents")),
                operation("PrintDocument", "print", "打印单据：打印", "Print document", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印单据：打印", "Print document", "POST", "/api/printing/documents/{id}/print")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintDocument", "list", "查询打印单据", "List print documents", "")), access);
    }
}
