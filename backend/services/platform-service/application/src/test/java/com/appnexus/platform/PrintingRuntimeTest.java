package com.appnexus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.adapter.jdbc.PrintRuntime;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 真实 MySQL 验证打印草稿、发布、设备、幂等任务、代理领取、回执和补打的完整状态闭环。 */
@SpringBootTest
@ActiveProfiles("local")
class PrintingRuntimeTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  private final JsonMapper mapper=JsonMapper.builder().build();
  private PrintRuntime runtime;

  @BeforeEach void prepare() {
    jdbc.update("DELETE FROM print_job");
    jdbc.update("DELETE FROM print_device");
    jdbc.update("DELETE FROM print_template_version");
    jdbc.update("DELETE FROM print_template");
    runtime = new PrintRuntime(jdbc, (context, permission) -> true, mapper);
  }

  @Test void publishedTemplatePrintsIdempotentlyAndUnknownJobRequiresReprint() throws Exception {
    String tenant=UUID.randomUUID().toString(), actor=UUID.randomUUID().toString();
    var design=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var template=runtime.saveTemplate(design,null,new PrintRuntime.TemplateDraft("delivery_note","Delivery note","A4","delivery.v1",mapper.createObjectNode(),mapper.readTree("{\"panels\":[{\"index\":0,\"width\":210,\"height\":297,\"paperType\":\"A4\",\"printElements\":[]}]}"),-1));
    String templateId=template.get("id").toString();
    runtime.publish(design,templateId,0);
    var device=runtime.saveDevice(design,null,new PrintRuntime.DeviceDraft("office_printer","Office printer","default","test-queue",mapper.createObjectNode(),true));
    String deviceId=device.get("id").toString();

    var submit=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var draft=new PrintRuntime.JobDraft("DELIVERY","D-100",templateId,"PRINTER",deviceId,1,mapper.readTree("{\"number\":\"D-100\"}"));
    var first=runtime.submit(submit,draft);
    var repeated=runtime.submit(submit,draft);
    assertThat(repeated.get("id")).isEqualTo(first.get("id"));

    var agent=new PrintContext(tenant,"partner-test-agent",UUID.randomUUID().toString());
    var claimed=runtime.claim(agent,"default","agent-1").orElseThrow();
    assertThat(claimed).containsKeys("template_json","data_snapshot","system_name");
    runtime.receipt(agent,first.get("id").toString(),new PrintRuntime.Receipt("SUBMITTED","agent-1",mapper.readTree("{\"code\":\"SYSTEM_QUEUE_ACCEPTED\"}")));

    var reprint=runtime.reprint(new PrintContext(tenant,actor,UUID.randomUUID().toString()),first.get("id").toString(),"Paper damaged");
    assertThat(reprint.get("parent_job_id")).isEqualTo(first.get("id"));
    runtime.claim(agent,"default","agent-1").orElseThrow();
    jdbc.update("UPDATE print_job SET claim_expires_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,reprint.get("id"));
    assertThat(runtime.claim(agent,"default","agent-1")).isEmpty();
    assertThat(runtime.jobs(design).stream().filter(row->row.get("id").equals(reprint.get("id"))).findFirst().orElseThrow().get("state")).isEqualTo("UNKNOWN");
  }
}
