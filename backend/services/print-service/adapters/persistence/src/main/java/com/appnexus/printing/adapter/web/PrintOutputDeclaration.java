package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 打印输出专用及公共入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintOutputDeclaration implements Provider {
    /** 原人员确认路由在外部主路由后登记，保留原入口标识及启停关系。 */
    public static final String PERSON_CONFIRM_ENTRY="PrintOutput.confirm@entry1";

    /** 人员状态查询入口，原共享操作和外部 HTTP 路由标识保持。 */
    public static final String PERSON_STATUS_ENTRY="PrintOutput.readStatus@personnel";

    private final ObjectProvider<PrintOutputCommandRuntime> commands;

    /** @param commands 延迟取得复用原业务端口的人员命令执行器 */
    public PrintOutputDeclaration(ObjectProvider<PrintOutputCommandRuntime> commands){
        this.commands=commands;
    }

    @Override public Set<String> commandOperations(){return Set.of("inspect","readStatus","create","confirm");}
    @Override public BusinessObjectCommandRuntime commandRuntime(){return commands.getIfAvailable();}

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
                // 读取权限供人员命令准入使用，原共享 HTTP 映射仍按各调用主体执行原用例授权。
                new Operation("readStatus","查询打印输出状态","Read print output status","print.job:read",
                    "PrintOutput.readStatus",null,List.of(new OperationAlias(PERSON_STATUS_ENTRY,
                        new PageOperation("print/index","/print/management","查询打印输出状态","Read print output status",
                            "GET","/api/printing/outputs/{id}"))),
                    new com.appnexus.contracts.businessobject.InteractionContract(
                        "PrintOutput.readStatus",1,"PrintOutput","readStatus","查询打印输出状态","Read print output status",
                        com.appnexus.contracts.businessobject.InteractionContract.Trigger.USER_ACTION,List.of()))), access);
    }
}
