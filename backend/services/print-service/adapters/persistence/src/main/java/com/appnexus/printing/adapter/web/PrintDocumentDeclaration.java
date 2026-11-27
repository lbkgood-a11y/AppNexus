package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import org.springframework.stereotype.Component;

/** 打印单据专用及公共入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintDocumentDeclaration implements Provider {
    private final org.springframework.beans.factory.ObjectProvider<PrintDocumentCommandRuntime> commands;

    /** @param commands 延迟取得固定打印命令端口，避免打印目录与对象声明循环装配 */
    public PrintDocumentDeclaration(org.springframework.beans.factory.ObjectProvider<PrintDocumentCommandRuntime> commands){
        this.commands=commands;
    }

    @Override public Set<String> commandOperations(){return Set.of("list","create","print");}
    @Override public BusinessObjectCommandRuntime commandRuntime(){return commands.getIfAvailable();}

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
                operation("PrintDocument", "list", "查询打印单据", "List print documents", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "查询打印单据", "List print documents", "GET", "/api/printing/documents"))), access);
    }
}
