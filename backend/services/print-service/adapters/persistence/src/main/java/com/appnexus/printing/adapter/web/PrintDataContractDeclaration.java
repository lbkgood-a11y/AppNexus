package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印数据契约专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintDataContractDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.datacontract", "", "打印数据契约", "Print data contracts", List.of())), Map.of());
        return new Descriptor("PrintDataContract", "打印数据契约", "Print Data Contract", "print", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("PrintDataContract", "list", "打印数据契约：查看列表", "View data contracts", "print.template:design",
                        new PageOperation("print/index", "/print/management", "打印数据契约：查看列表", "View data contracts", "GET", "/api/printing/design-contracts")),
                operation("PrintDataContract", "create", "打印数据契约：新增", "Create data contract", "print.template:design",
                        new PageOperation("print/index", "/print/management", "打印数据契约：新增", "Create data contract", "POST", "/api/printing/design-contracts"))), access);
    }
}
