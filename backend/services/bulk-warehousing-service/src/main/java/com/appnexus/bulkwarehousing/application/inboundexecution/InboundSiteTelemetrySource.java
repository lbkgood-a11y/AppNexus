package com.appnexus.bulkwarehousing.application.inboundexecution;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 已受信的现场采集适配端口。 */
public interface InboundSiteTelemetrySource {
    /** 采集方给出的同车次读取及受控图片。 */
    record Reading(String station,String kg,boolean stable,Instant observedAt,String capture) {}
    /** @return 当前适配是否运行于模拟模式 */
    boolean simulated();
    /** @return 同租户同车次快照，断线或无效输入为空 */
    Optional<Reading> latest(UUID tenant,UUID task,boolean allowCapture);
}
