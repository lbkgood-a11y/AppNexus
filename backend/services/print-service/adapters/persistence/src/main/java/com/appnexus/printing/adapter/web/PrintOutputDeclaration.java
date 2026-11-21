package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印输出专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintOutputDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.output", "", "打印输出", "Print outputs", List.of())), Map.of());
        return new Descriptor("PrintOutput", "打印输出", "Print Output", "print", 2, Category.TRANSACTION, List.of(), List.of(
                operation("PrintOutput", "inspect", "打印输出：检查依赖", "Inspect printing dependencies", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印输出：检查依赖", "Inspect printing dependencies", "GET", "/api/printing/documents/{id}/preflight")),
                operation("PrintOutput", "create", "打印输出：新增", "Create output", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印输出：新增", "Create output", "POST", "/api/printing/documents/{id}/outputs")),
                operation("PrintOutput", "confirm", "打印输出：确认输出", "Confirm output", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印输出：确认输出", "Confirm output", "POST", "/api/printing/external/outputs/{id}/confirm"),
                        new PageOperation("print/index", "/print/management", "打印输出：确认输出", "Confirm output", "POST", "/api/printing/outputs/{id}/confirm")),
                operation("PrintOutput", "preview", "打印输出：预演", "View print preview", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印输出：预演", "View print preview", "GET", "/api/printing/external/outputs/{id}/pdf"),
                        new PageOperation("print/index", "/print/management", "打印输出：预演", "View print preview", "GET", "/api/printing/outputs/{id}/pdf")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintOutput", "externalSubmit", "提交外部打印输出", "Submit external print output", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintOutput", "readStatus", "查询打印输出状态", "Read print output status", "")), access);
    }
}
