package com.appnexus.approval.adapter.flowable;

import com.appnexus.approval.core.port.ApprovalEngine;
import java.util.Objects;
import org.flowable.engine.ProcessEngine;

/**
 * Flowable 内嵌引擎适配器；禁止向调用方泄漏引擎实体或绕过审批应用入口。
 * 引擎生命周期由组合应用管理，适配器不单独关闭共享引擎。
 */
public final class FlowableApprovalEngine implements ApprovalEngine {
    private final ProcessEngine processEngine;

    /**
     * 绑定已经完成数据库与事务装配的引擎。
     * @param processEngine 非空的服务端内嵌引擎，不接受客户端提供的实例
     */
    public FlowableApprovalEngine(ProcessEngine processEngine) {
        this.processEngine = Objects.requireNonNull(processEngine);
    }

    @Override
    public String engineName() {
        return processEngine.getName();
    }
}
