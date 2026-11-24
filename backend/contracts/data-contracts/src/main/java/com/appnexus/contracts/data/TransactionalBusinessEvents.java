package com.appnexus.contracts.data;

import java.util.Set;
import java.util.UUID;

/** 组合应用同库 Outbox 的公开业务契约。事件只携带稳定引用，数据由所属模块重读。 */
public interface TransactionalBusinessEvents {
    /** 当前接入的持久事件类型。 */
    enum Type { INBOUND_RECEIPT_POSTED, INBOUND_QUALITY_DECIDED }

    /**
     * @param tenant 可信租户 UUID，禁止使用平台零 UUID
     * @param type 已接入的版本化业务事件类型
     * @param subjectId 该类型规定的单据 UUID，禁止存储原始业务数据
     * @return 事务提交后可领取的事件 UUID
     * @throws RuntimeException 当前没有业务事务或事件追加失败
     */
    UUID publish(UUID tenant, Type type, UUID subjectId);

    /** 已领取且待以同库消费事务处理的事件引用。 */
    record Delivery(UUID id, UUID tenant, Type type, UUID subjectId) {}

    /** 组合应用事件分派端口，同类型允许多个稳定标识不同的事务消费者。 */
    interface Consumer {
        /** @return 当前消费者处理的稳定类型集合 */
        Set<Type> types();
        /** @return 消费幂等使用的固定名称 */
        String consumerId();
        /**
         * @param delivery 可信调度器已领取的引用，本方法在平台消费事务中运行
         * @throws RuntimeException 消费失败，副作用与回执一起回滚
         */
        void consume(Delivery delivery);
    }
}
