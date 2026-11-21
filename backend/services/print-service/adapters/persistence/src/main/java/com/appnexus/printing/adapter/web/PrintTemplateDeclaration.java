package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印模板专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintTemplateDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.template", "", "打印模板", "Print templates", List.of()),
                new DataResource("print.template.version", "print.template", "打印模板版本", "Print template versions", List.of())), Map.of());
        return new Descriptor("PrintTemplate", "打印模板", "Print Template", "print", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("PrintTemplate", "list", "打印模板：查看列表", "View templates", "print.template:read",
                        new PageOperation("print/index", "/print/management", "打印模板：查看列表", "View templates", "GET", "/api/printing/templates")),
                operation("PrintTemplate", "read", "打印模板：查看", "View template", "print.template:read",
                        new PageOperation("print/index", "/print/management", "打印模板：查看", "View template", "GET", "/api/printing/templates/{id}")),
                operation("PrintTemplate", "create", "打印模板：新增", "Create template", "print.template:design",
                        new PageOperation("print/index", "/print/management", "打印模板：新增", "Create template", "POST", "/api/printing/templates")),
                operation("PrintTemplate", "update", "打印模板：修改", "Save template", "print.template:design",
                        new PageOperation("print/index", "/print/management", "打印模板：修改", "Save template", "PUT", "/api/printing/templates/{id}")),
                operation("PrintTemplate", "publish", "打印模板：发布", "Publish template", "print.template:publish",
                        new PageOperation("print/index", "/print/management", "打印模板：发布", "Publish template", "POST", "/api/printing/templates/{id}/publish")),
                operation("PrintTemplate", "versions", "打印模板：查看版本", "View template versions", "print.template:read",
                        new PageOperation("print/index", "/print/management", "打印模板：查看版本", "View template versions", "GET", "/api/printing/templates/{id}/versions")),
                operation("PrintTemplate", "restore", "打印模板：恢复历史版本", "Restore template version", "print.template:design",
                        new PageOperation("print/index", "/print/management", "打印模板：恢复历史版本", "Restore template version", "POST", "/api/printing/templates/{id}/restore"))), access);
    }
}
