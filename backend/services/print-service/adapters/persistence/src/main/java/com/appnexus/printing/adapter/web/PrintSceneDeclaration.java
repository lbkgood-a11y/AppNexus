package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.printing.api.PrintSceneManagement;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 打印场景专用及公共入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintSceneDeclaration implements Provider {
    private final ObjectProvider<PrintSceneCommandRuntime> commands;

    /** @param commands 延迟取得原配置用例的类型化命令端口 */
    public PrintSceneDeclaration(ObjectProvider<PrintSceneCommandRuntime> commands){this.commands=commands;}
    @Override public Set<String> commandOperations(){return Set.of("list","create","update");}
    @Override public BusinessObjectCommandRuntime commandRuntime(){return commands.getIfAvailable();}
    @Override public Set<String> commandPermissions(Operation operation){
        return "list".equals(operation.operationCode())?PrintSceneManagement.SCENE_READ_PERMISSIONS:Set.of(operation.permissionCode());
    }

    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.scene", "", "打印场景", "Print scenes", List.of())), Map.of());
        return new Descriptor("PrintScene", "打印场景", "Print Scene", "print", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("PrintScene", "list", "打印场景：查看列表", "View scenes", "print.job:submit",
                        new PageOperation("print/index", "/print/management", "打印场景：查看列表", "View scenes", "GET", "/api/printing/scenes")),
                operation("PrintScene", "create", "打印场景：新增", "Create scene", "print.template:publish",
                        new PageOperation("print/index", "/print/management", "打印场景：新增", "Create scene", "POST", "/api/printing/scenes")),
                operation("PrintScene", "update", "打印场景：修改", "Save scene", "print.template:publish",
                        new PageOperation("print/index", "/print/management", "打印场景：修改", "Save scene", "PUT", "/api/printing/scenes/{id}"))), access);
    }
}
