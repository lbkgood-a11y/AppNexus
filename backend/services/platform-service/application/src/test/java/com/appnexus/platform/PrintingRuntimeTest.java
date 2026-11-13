package com.appnexus.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.adapter.jdbc.PrintRuntime;
import com.appnexus.printing.adapter.jdbc.PrintFlow;
import com.appnexus.printing.adapter.jdbc.PrintOutputs;
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
    jdbc.update("DELETE FROM print_output_request");
    jdbc.update("DELETE FROM print_job_artifact");
    jdbc.update("DELETE FROM print_job");
    jdbc.update("DELETE FROM print_document");
    jdbc.update("DELETE FROM print_scene");
    jdbc.update("DELETE FROM print_data_contract");
    jdbc.update("DELETE FROM print_agent_queue");
    jdbc.update("DELETE FROM print_device");
    jdbc.update("DELETE FROM print_template_version");
    jdbc.update("DELETE FROM print_template");
    runtime = new PrintRuntime(jdbc, (context, permission) -> true, mapper);
  }

  @Test void publishedTemplatePrintsIdempotentlyAndUnknownJobRequiresReprint() throws Exception {
    String tenant=UUID.randomUUID().toString(), actor=UUID.randomUUID().toString();
    var design=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var template=runtime.saveTemplate(design,null,new PrintRuntime.TemplateDraft("delivery_note","Delivery note","A4","warehouse.outbound.v1",mapper.createObjectNode(),mapper.readTree("{\"panels\":[{\"index\":0,\"width\":210,\"height\":297,\"paperType\":\"A4\",\"printElements\":[]}]}"),-1));
    String templateId=template.get("id").toString();
    runtime.publish(design,templateId,0);
    assertThat(runtime.restoreVersion(design,templateId,1,0).get("draft_revision")).isEqualTo(1L);
    assertThatThrownBy(()->runtime.restoreVersion(design,templateId,1,0)).hasMessage("PRINT_TEMPLATE_CONFLICT");
    assertThat(runtime.versions(design,templateId)).hasSize(1);
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
    String jobId=first.get("id").toString();
    var accepted=new PrintRuntime.Receipt("SUBMITTED","agent-1",mapper.readTree("{\"code\":\"SYSTEM_QUEUE_ACCEPTED\"}"));
    assertThatThrownBy(()->runtime.receipt(agent,jobId,new PrintRuntime.Receipt("SUCCEEDED","agent-1",mapper.createObjectNode())))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThatThrownBy(()->runtime.receipt(agent,jobId,new PrintRuntime.Receipt("SUBMITTED","another-agent",accepted.result())))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    runtime.receipt(agent,jobId,accepted);
    assertThat(runtime.receipt(agent,jobId,accepted).get("state")).isEqualTo("SUBMITTED");
    assertThatThrownBy(()->runtime.receipt(agent,jobId,new PrintRuntime.Receipt("FAILED","agent-1",mapper.createObjectNode())))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    var completed=new PrintRuntime.Receipt("SUCCEEDED","agent-1",mapper.readTree("{\"code\":\"DEVICE_CONFIRMED\"}"));
    runtime.receipt(agent,jobId,completed);
    assertThat(runtime.receipt(agent,jobId,completed).get("state")).isEqualTo("SUCCEEDED");
    assertThatThrownBy(()->runtime.receipt(agent,jobId,accepted)).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");

    var reprint=runtime.reprint(new PrintContext(tenant,actor,UUID.randomUUID().toString()),first.get("id").toString(),"Paper damaged");
    assertThat(reprint.get("parent_job_id")).isEqualTo(first.get("id"));
    runtime.claim(agent,"default","agent-1").orElseThrow();
    jdbc.update("UPDATE print_job SET claim_expires_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,reprint.get("id"));
    assertThatThrownBy(()->runtime.receipt(agent,reprint.get("id").toString(),accepted))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThat(runtime.claim(agent,"default","agent-1")).isEmpty();
    assertThat(runtime.jobs(design).stream().filter(row->row.get("id").equals(reprint.get("id"))).findFirst().orElseThrow().get("state")).isEqualTo("UNKNOWN");

    var pdfJob=runtime.submit(new PrintContext(tenant,actor,UUID.randomUUID().toString()),
        new PrintRuntime.JobDraft("DELIVERY","D-100",templateId,"PDF",null,1,mapper.createObjectNode()));
    String pdfId=pdfJob.get("id").toString();
    runtime.claim(agent,"default","agent-1").orElseThrow();
    byte[] pdf="%PDF-1.7\nTEST-ONLY".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    assertThatThrownBy(()->runtime.uploadPdf(agent,pdfId,"another-agent",pdf)).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThat(runtime.uploadPdf(agent,pdfId,"agent-1",pdf).get("state")).isEqualTo("SUCCEEDED");
    assertThat(runtime.uploadPdf(agent,pdfId,"agent-1",pdf).get("state")).isEqualTo("SUCCEEDED");
    assertThat(runtime.downloadPdf(design,pdfId)).isEqualTo(pdf);
    assertThatThrownBy(()->runtime.downloadPdf(new PrintContext(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString()),pdfId))
        .hasMessage("PRINT_PDF_NOT_FOUND");
    assertThatThrownBy(()->runtime.uploadPdf(agent,pdfId,"agent-1","%PDF-other".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");

    var flow=new PrintFlow(jdbc,(context,permission)->true,runtime,mapper);
    var custom=mapper.readTree("{\"code\":\"custom.note.v1\",\"nameZh\":\"自定义单据\",\"nameEn\":\"Custom note\",\"fields\":[{\"key\":\"recipient\",\"labelZh\":\"收货人\",\"labelEn\":\"Recipient\",\"sample\":\"测试\"}]}");
    assertThat(flow.createContract(design,custom)).hasSize(2);
    assertThat(flow.contracts(new PrintContext(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString()))).hasSize(1);
    flow.report(agent,new PrintFlow.QueueReport("default","agent-1",List.of("test-queue","中文队列")));
    assertThat(flow.queues(design)).hasSize(2);
    var scenes=flow.saveScene(design,null,new PrintFlow.Scene("outbound","出仓单","warehouse.outbound.v1",templateId,1,true,-1));
    String sceneId=scenes.getFirst().get("id").toString();
    // 外部调用依赖场景编码稳定；绕过页面修改也必须失败，且不得影响原有入口。
    assertThatThrownBy(()->flow.saveScene(design,sceneId,new PrintFlow.Scene("renamed","出仓单","warehouse.outbound.v1",templateId,1,true,0)))
        .hasMessage("PRINT_SCENE_CODE_IMMUTABLE");
    assertThat(flow.scenes(design).getFirst().get("code")).isEqualTo("outbound");
    assertThatThrownBy(()->flow.saveScene(design,null,new PrintFlow.Scene("outbound","重复场景","warehouse.outbound.v1",templateId,1,true,-1)))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

    var documents=flow.saveDocument(design,new PrintFlow.Document(sceneId,"OUT-REAL-001",mapper.readTree("{\"documentNumber\":\"OUT-REAL-001\",\"items\":[{\"itemName\":\"实际录入物料\",\"quantity\":\"2\"}]}")));
    String documentId=documents.getFirst().get("id").toString();
    var options=new PrintFlow.Output(null,"PDF",1);
    var request=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var configured=flow.print(request,documentId,options);
    assertThat(flow.print(request,documentId,options).get("id")).isEqualTo(configured.get("id"));
    assertThat(configured.get("source_type")).isEqualTo("CONFIGURED_DOCUMENT");
    assertThatThrownBy(()->flow.print(new PrintContext(tenant,UUID.randomUUID().toString(),UUID.randomUUID().toString()),documentId,options)).hasMessage("PRINT_DOCUMENT_NOT_FOUND");
    assertThatThrownBy(()->flow.saveDocument(design,new PrintFlow.Document(sceneId,"BAD",mapper.readTree("{\"unexpected\":\"value\"}")))).hasMessage("PRINT_CONTRACT_INVALID");
    var outputs=new PrintOutputs(jdbc,(context,permission)->true,runtime,flow,mapper);
    var bridge=new com.appnexus.printing.adapter.jdbc.BusinessPrintingAdapter(jdbc,outputs,(context,permission)->true);
    assertThat(bridge.rendererOnline(design)).isTrue();
    jdbc.update("UPDATE print_agent_queue SET seen_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 121 SECOND) WHERE tenant_id=UUID_TO_BIN(?)",tenant);
    assertThat(bridge.rendererOnline(design)).isFalse();
    flow.report(agent,new PrintFlow.QueueReport("default","agent-1",List.of("test-queue","中文队列")));
    assertThat(bridge.rendererOnline(design)).isTrue();
    assertThat(bridge.scenes(design,"warehouse.outbound.v1")).hasSize(1);
    assertThat(bridge.scenes(new PrintContext(UUID.randomUUID().toString(),design.actorId(),UUID.randomUUID().toString()),"warehouse.outbound.v1")).isEmpty();
    // 公开业务端口复用场景契约与幂等输出，数据先由业务端授权，打印端仍拒绝不兼容契约。
    var businessContext=new PrintContext(tenant,"partner-business-test",UUID.randomUUID().toString());
    var businessInput=new com.appnexus.contracts.printing.BusinessPrinting.Request("outbound","warehouse.outbound.v1","BUSINESS-1",Map.of("documentNumber","BUSINESS-1"),"PREVIEW",deviceId,1);
    assertThatThrownBy(()->bridge.request(businessContext,new com.appnexus.contracts.printing.BusinessPrinting.Request("outbound","wrong.v1","BUSINESS-1",Map.of(),"PREVIEW",deviceId,1))).hasMessage("PRINT_SCENE_UNAVAILABLE");
    // 后面的代理领取断言依赖队列顺序，业务端口的额外任务在本用例结束再创建。
    var external=new PrintContext(tenant,"partner-output-test",UUID.randomUUID().toString());
    var input=new PrintOutputs.Input("outbound","OUT-EXT-001",mapper.readTree("{\"documentNumber\":\"OUT-EXT-001\"}"),"PREVIEW",deviceId,2,"warehouse.outbound.v1");
    var preview=outputs.external(external,input);String outputId=preview.get("id").toString();
    assertThat(outputs.external(external,input).get("id")).isEqualTo(outputId);
    assertThatThrownBy(()->outputs.external(external,new PrintOutputs.Input("outbound","OTHER",input.data(),"PREVIEW",deviceId,2,"warehouse.outbound.v1"))).hasMessage("PRINT_IDEMPOTENCY_CONFLICT");
    assertThatThrownBy(()->outputs.confirm(external,outputId,deviceId)).hasMessage("PRINT_PREVIEW_NOT_READY");
    assertThatThrownBy(()->outputs.status(new PrintContext(tenant,"partner-another",UUID.randomUUID().toString()),outputId)).hasMessage("PRINT_OUTPUT_NOT_FOUND");
    // 前一项配置式 PDF 仍在队列，显式完成后才领取外部预览，确认前绝不出现 PRINTER 子任务。
    var queued=runtime.claim(agent,"default","agent-1").orElseThrow();
    runtime.uploadPdf(agent,queued.get("id").toString(),"agent-1",pdf);
    var previewJob=runtime.claim(agent,"default","agent-1").orElseThrow();
    assertThat(previewJob.get("id")).isEqualTo(preview.get("jobId"));
    runtime.uploadPdf(agent,preview.get("jobId").toString(),"agent-1",pdf);
    assertThat(outputs.pdf(external,outputId)).isEqualTo(pdf);
    var confirmed=outputs.confirm(external,outputId,deviceId);
    assertThat(confirmed.get("confirmedJobId")).isNotNull();
    var businessOutput=bridge.request(businessContext,businessInput);
    assertThat(bridge.request(businessContext,businessInput).get("id")).isEqualTo(businessOutput.get("id"));
    assertThat(bridge.history(businessContext,"BUSINESS-1")).singleElement().satisfies(event->assertThat(event.get("id")).isEqualTo(businessOutput.get("id")));
    assertThat(bridge.history(new PrintContext(tenant,"partner-other",UUID.randomUUID().toString()),"BUSINESS-1")).isEmpty();
    assertThat(outputs.confirm(external,outputId,deviceId).get("confirmedJobId")).isEqualTo(confirmed.get("confirmedJobId"));
    var physical=runtime.claim(agent,"default","agent-1").orElseThrow();
    assertThat(physical.get("copies")).isEqualTo(2);
    assertThat(physical.get("output_type")).isEqualTo("PRINTER");
    assertThat(outputs.status(external,outputId).get("deliveryState")).isNotNull();
    var silent=outputs.external(new PrintContext(tenant,"partner-output-test",UUID.randomUUID().toString()),new PrintOutputs.Input("outbound","OUT-SILENT",input.data(),"SILENT",deviceId,1,"warehouse.outbound.v1"));
    assertThat(silent.get("mode")).isEqualTo("SILENT");
    flow.saveScene(design,sceneId,new PrintFlow.Scene("outbound","出仓单","warehouse.outbound.v1",templateId,1,false,0));
    assertThatThrownBy(()->flow.print(new PrintContext(tenant,actor,UUID.randomUUID().toString()),documentId,options)).hasMessage("PRINT_SCENE_UNAVAILABLE");
  }
  @Test void typedContractRejectsInvalidDataAndFreezesSchema(){
    String tenant=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString();var c=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var flow=new PrintFlow(jdbc,(context,permission)->true,runtime,mapper);
    flow.businessContracts(List.of(()->List.of(Map.of("code","test.typed.v1","version",1,"nameZh","测试契约","nameEn","Typed test","fields",List.of(Map.of("key","days","type","NUMBER","required",true,"unit","天"))))));
    var template=runtime.saveTemplate(c,null,new PrintRuntime.TemplateDraft("typed","类型测试","A4","test.typed.v1",mapper.createObjectNode(),mapper.readTree("{\"panels\":[]}"),-1));
    String templateId=template.get("id").toString();runtime.publish(c,templateId,0);
    String scene=flow.saveScene(c,null,new PrintFlow.Scene("typed","类型测试","test.typed.v1",templateId,1,true,-1)).getFirst().get("id").toString();
    assertThatThrownBy(()->flow.saveDocument(c,new PrintFlow.Document(scene,"BAD",mapper.readTree("{\"days\":\"5\"}")))).hasMessage("DATA_FIELD_TYPE_INVALID");
    assertThatThrownBy(()->flow.saveDocument(c,new PrintFlow.Document(scene,"MISSING",mapper.createObjectNode()))).hasMessage("DATA_FIELD_REQUIRED");
    String document=flow.saveDocument(c,new PrintFlow.Document(scene,"GOOD",mapper.readTree("{\"days\":5}"))).getFirst().get("id").toString();
    var job=flow.print(new PrintContext(tenant,actor,UUID.randomUUID().toString()),document,new PrintFlow.Output(null,"PDF",1));
    assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(data_contract_schema,'$.code')) FROM print_job WHERE id=UUID_TO_BIN(?)",String.class,job.get("id"))).isEqualTo("test.typed.v1");
  }

}
