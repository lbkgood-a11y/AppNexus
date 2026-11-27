package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.time.Instant;
import java.util.UUID;

/** 工作台只读现场数据，不替代业务磅票。 */
public interface ReadInboundSiteTelemetry {
    String PATH="/api/bulk-inbound-workbench/tasks/{id}/site";
    /** 就绪、过期或未接入。读数以千克十进制文本传输。 */
    record InboundSiteReading(String state, boolean simulated, String station, String kg, Boolean stable,
                              Instant observedAt, String capture) {}
    /** @return 可信租户和作业范围内的最新读数。 */
    InboundSiteReading read(VerifiedTenantContext context,UUID task);
}
