package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印场景专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintSceneDeclaration implements Provider {
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
