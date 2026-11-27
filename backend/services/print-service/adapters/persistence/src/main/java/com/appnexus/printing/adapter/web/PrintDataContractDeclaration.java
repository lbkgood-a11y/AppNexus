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

/** 打印数据契约专用及公共入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintDataContractDeclaration implements Provider {
    private final ObjectProvider<PrintDataContractCommandRuntime> commands;

    /** @param commands 延迟取得原配置用例的类型化命令端口 */
    public PrintDataContractDeclaration(ObjectProvider<PrintDataContractCommandRuntime> commands){this.commands=commands;}
    @Override public Set<String> commandOperations(){return Set.of("list","create");}
    @Override public BusinessObjectCommandRuntime commandRuntime(){return commands.getIfAvailable();}
    @Override public Set<String> commandPermissions(Operation operation){
        return "list".equals(operation.operationCode())?PrintSceneManagement.CONTRACT_READ_PERMISSIONS:Set.of(operation.permissionCode());
    }

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
