package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 打印设备专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PrintDeviceDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("print.device", "", "打印设备", "Print devices", List.of()),
                new DataResource("print.device.queue", "print.device", "设备队列", "Device queues", List.of())), Map.of());
        return new Descriptor("PrintDevice", "打印设备", "Print Device", "print", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("PrintDevice", "discoverQueues", "打印设备：发现设备队列", "View client queues", "print.device:manage",
                        new PageOperation("print/index", "/print/management", "打印设备：发现设备队列", "View client queues", "GET", "/api/printing/client-queues")),
                operation("PrintDevice", "list", "打印设备：查看列表", "View devices", "print.device:manage",
                        new PageOperation("print/index", "/print/management", "打印设备：查看列表", "View devices", "GET", "/api/printing/devices")),
                operation("PrintDevice", "create", "打印设备：新增", "Create device", "print.device:manage",
                        new PageOperation("print/index", "/print/management", "打印设备：新增", "Create device", "POST", "/api/printing/devices")),
                operation("PrintDevice", "update", "打印设备：修改", "Edit device", "print.device:manage",
                        new PageOperation("print/index", "/print/management", "打印设备：修改", "Edit device", "PUT", "/api/printing/devices/{id}")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintDevice", "agentIdentity", "查询打印代理身份", "Read print agent identity", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PrintDevice", "reportQueues", "上报打印代理队列", "Report print queues", "")), access);
    }
}
