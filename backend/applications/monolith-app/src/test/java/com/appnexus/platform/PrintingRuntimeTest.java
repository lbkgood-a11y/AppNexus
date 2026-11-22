package com.appnexus.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.application.PrintRuntimeService;
import com.appnexus.printing.application.PrintSceneService;
import com.appnexus.printing.application.PrintOutputService;
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
  @Autowired com.appnexus.platform.authorization.ScopedWriteGate writes;
  @Autowired JdbcTemplate jdbc;
  private final JsonMapper mapper=JsonMapper.builder().build();
  private PrintRuntimeService runtime;
  @Autowired com.appnexus.printing.adapter.persistence.PrintRuntimeMapper runtimeMapper;
  @Autowired com.appnexus.printing.adapter.persistence.PrintReceiptMapper receiptMapper;
  @Autowired com.appnexus.printing.adapter.persistence.PrintFlowMapper sceneMapper;
  @Autowired com.appnexus.printing.adapter.persistence.PrintOutputsMapper outputMapper;
  @Autowired com.appnexus.printing.adapter.persistence.BusinessPrintingMapper businessMapper;
  private com.appnexus.printing.application.PrintTransaction transaction;
  private final java.util.concurrent.atomic.AtomicBoolean realTransactionObserved=new java.util.concurrent.atomic.AtomicBoolean();


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
    // 与生产使用相同事务适配器，观察真实数据库事务，避免只断言代理存在却漏掉完整用例边界。
    realTransactionObserved.set(false);
    var delegate=new com.appnexus.printing.adapter.transaction.SpringPrintTransaction(new org.springframework.jdbc.datasource.DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
    transaction=new com.appnexus.printing.application.PrintTransaction(){
      public <T>T required(java.util.function.Supplier<T> work){return delegate.required(()->{realTransactionObserved.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return work.get();});}
    };
    runtime=new PrintRuntimeService(fixturePrintPlatform(),new com.appnexus.printing.adapter.codec.JacksonPrintRuntimeContent(),new com.appnexus.printing.application.PrintReceiptService(new com.appnexus.printing.adapter.persistence.MyBatisPrintReceiptStore(receiptMapper,mapper),fixturePrintPlatform()),new com.appnexus.printing.adapter.persistence.MyBatisPrintRuntimeRepository(runtimeMapper),transaction);

  }

  /** 验证实际用例进入真实事务，替代仅检查AOP代理类型；业务状态、回滚与幂等断言保持原范围。 */
  @AfterEach void observesRealUseCaseTransaction(){assertThat(realTransactionObserved.get()).isTrue();}

  @Test void publishedTemplatePrintsIdempotentlyAndUnknownJobRequiresReprint() throws Exception {
    String tenant=UUID.randomUUID().toString(), actor=UUID.randomUUID().toString();
    var design=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var template=runtime.saveTemplate(design,null,new com.appnexus.printing.api.PrintData.TemplateDraft("delivery_note","Delivery note","A4","warehouse.outbound.v1",mapper.createObjectNode(),mapper.readTree("{\"panels\":[{\"index\":0,\"width\":210,\"height\":297,\"paperType\":\"A4\",\"printElements\":[]}]}"),-1));
    String templateId=template.get("id").toString();
    assertThatThrownBy(()->runtimeMapper.freezeTemplateVersion(1,actor,tenant,templateId))
        .hasRootCauseMessage("SCOPED_WRITE_PLAN_REQUIRED");
    runtime.publish(design,templateId,0);
    assertThat(runtime.restoreVersion(design,templateId,1,0).get("draft_revision")).isEqualTo(1L);
    assertThatThrownBy(()->runtime.restoreVersion(design,templateId,1,0)).hasMessage("PRINT_TEMPLATE_CONFLICT");
    assertThat(runtime.versions(design,templateId)).hasSize(1);
    var device=runtime.saveDevice(design,null,new com.appnexus.printing.api.PrintData.DeviceDraft("office_printer","Office printer","default","test-queue",mapper.createObjectNode(),true));
    String deviceId=device.get("id").toString();

    var submit=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var draft=new com.appnexus.printing.api.PrintData.JobDraft("DELIVERY","D-100",templateId,"PRINTER",deviceId,1,mapper.readTree("{\"number\":\"D-100\"}"));
    var first=runtime.submit(submit,draft);
    var repeated=runtime.submit(submit,draft);
    assertThat(repeated.get("id")).isEqualTo(first.get("id"));

    var agent=new PrintContext(tenant,"partner-test-agent",UUID.randomUUID().toString());
    var otherPrincipal=new PrintContext(tenant,"partner-another-agent",UUID.randomUUID().toString());
    var claimed=runtime.claim(agent,"default","agent-1").orElseThrow();
    assertThat(claimed).containsKeys("template_json","data_snapshot","system_name");
    String jobId=first.get("id").toString();
    assertThatThrownBy(()->receiptMapper.update(tenant,jobId,agent.actorId(),"SUBMITTED","{}"))
        .hasRootCauseMessage("SCOPED_WRITE_PLAN_REQUIRED");
    var accepted=new com.appnexus.printing.api.PrintData.Receipt("SUBMITTED","agent-1",mapper.readTree("{\"code\":\"SYSTEM_QUEUE_ACCEPTED\"}"));
    assertThatThrownBy(()->runtime.receipt(agent,jobId,new com.appnexus.printing.api.PrintData.Receipt("SUCCEEDED","agent-1",mapper.createObjectNode())))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThatThrownBy(()->runtime.receipt(agent,jobId,new com.appnexus.printing.api.PrintData.Receipt("SUBMITTED","another-agent",accepted.result())))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThatThrownBy(()->runtime.receipt(otherPrincipal,jobId,accepted)).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    runtime.receipt(agent,jobId,accepted);
    assertThat(runtime.receipt(agent,jobId,accepted).get("state")).isEqualTo("SUBMITTED");
    assertThatThrownBy(()->runtime.receipt(agent,jobId,new com.appnexus.printing.api.PrintData.Receipt("FAILED","agent-1",mapper.createObjectNode())))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    var completed=new com.appnexus.printing.api.PrintData.Receipt("SUCCEEDED","agent-1",mapper.readTree("{\"code\":\"DEVICE_CONFIRMED\"}"));
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
        new com.appnexus.printing.api.PrintData.JobDraft("DELIVERY","D-100",templateId,"PDF",null,1,mapper.createObjectNode()));
    String pdfId=pdfJob.get("id").toString();
    runtime.claim(agent,"default","agent-1").orElseThrow();
    byte[] pdf="%PDF-1.7\nTEST-ONLY".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    assertThatThrownBy(()->runtime.uploadPdf(agent,pdfId,"another-agent",pdf)).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThatThrownBy(()->runtime.uploadPdf(otherPrincipal,pdfId,"agent-1",pdf)).hasMessage("PRINT_JOB_RECEIPT_CONFLICT");
    assertThat(runtime.uploadPdf(agent,pdfId,"agent-1",pdf).get("state")).isEqualTo("SUCCEEDED");
    assertThat(runtime.uploadPdf(agent,pdfId,"agent-1",pdf).get("state")).isEqualTo("SUCCEEDED");
    assertThat(runtime.downloadPdf(design,pdfId)).isEqualTo(pdf);
    assertThatThrownBy(()->runtime.downloadPdf(new PrintContext(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString()),pdfId))
        .hasMessage("PRINT_PDF_NOT_FOUND");
    assertThatThrownBy(()->runtime.uploadPdf(agent,pdfId,"agent-1","%PDF-other".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
        .hasMessage("PRINT_JOB_RECEIPT_CONFLICT");

    var flow=new PrintSceneService(fixturePrintPlatform(),runtime,new com.appnexus.printing.adapter.codec.JacksonPrintSceneContent(),new com.appnexus.printing.adapter.persistence.MyBatisPrintSceneRepository(sceneMapper),transaction);
    var custom=mapper.readTree("{\"code\":\"custom.note.v1\",\"nameZh\":\"自定义单据\",\"nameEn\":\"Custom note\",\"fields\":[{\"key\":\"recipient\",\"labelZh\":\"收货人\",\"labelEn\":\"Recipient\",\"sample\":\"测试\"}]}");
    assertThat(flow.createContract(design,custom)).hasSize(2);
    assertThat(flow.contracts(new PrintContext(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString()))).hasSize(1);
    flow.report(agent,new com.appnexus.printing.api.PrintData.QueueReport("default","agent-1",List.of("test-queue","中文队列")));
    var secondQueueActor=new PrintContext(tenant,"partner-queue-other",UUID.randomUUID().toString());
    flow.report(secondQueueActor,new com.appnexus.printing.api.PrintData.QueueReport("default","agent-1",List.of("other-queue")));
    flow.report(secondQueueActor,new com.appnexus.printing.api.PrintData.QueueReport("default","agent-1",List.of()));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(?) AND agent_principal=?",
        Integer.class,tenant,agent.actorId())).isEqualTo(2);
    assertThatThrownBy(()->sceneMapper.deleteAgentQueues(tenant,"default","agent-1",agent.actorId()))
        .hasRootCauseMessage("SCOPED_WRITE_PLAN_REQUIRED");
    assertThat(flow.queues(design)).hasSize(2);
    var scenes=flow.saveScene(design,null,new com.appnexus.printing.api.PrintData.SceneRequest("outbound","出仓单","warehouse.outbound.v1",templateId,1,true,-1));
    String sceneId=scenes.getFirst().get("id").toString();
    // 外部调用依赖场景编码稳定；绕过页面修改也必须失败，且不得影响原有入口。
    assertThatThrownBy(()->flow.saveScene(design,sceneId,new com.appnexus.printing.api.PrintData.SceneRequest("renamed","出仓单","warehouse.outbound.v1",templateId,1,true,0)))
        .hasMessage("PRINT_SCENE_CODE_IMMUTABLE");
    assertThat(flow.scenes(design).getFirst().get("code")).isEqualTo("outbound");
    assertThatThrownBy(()->flow.saveScene(design,null,new com.appnexus.printing.api.PrintData.SceneRequest("outbound","重复场景","warehouse.outbound.v1",templateId,1,true,-1)))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

    var documents=flow.saveDocument(design,new com.appnexus.printing.api.PrintData.DocumentRequest(sceneId,"OUT-REAL-001",mapper.readTree("{\"documentNumber\":\"OUT-REAL-001\",\"items\":[{\"itemName\":\"实际录入物料\",\"quantity\":\"2\"}]}")));
    String documentId=documents.getFirst().get("id").toString();
    var options=new com.appnexus.printing.api.PrintData.DocumentPrintRequest(null,"PDF",1);
    var request=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var configured=flow.print(request,documentId,options);
    assertThat(flow.print(request,documentId,options).get("id")).isEqualTo(configured.get("id"));
    assertThat(configured.get("source_type")).isEqualTo("CONFIGURED_DOCUMENT");
    assertThatThrownBy(()->flow.print(new PrintContext(tenant,UUID.randomUUID().toString(),UUID.randomUUID().toString()),documentId,options)).hasMessage("PRINT_BUSINESS_OBJECT_NOT_FOUND");
    assertThatThrownBy(()->flow.saveDocument(design,new com.appnexus.printing.api.PrintData.DocumentRequest(sceneId,"BAD",mapper.readTree("{\"unexpected\":\"value\"}")))).hasMessage("PRINT_CONTRACT_INVALID");
    var outputs=new PrintOutputService(fixturePrintPlatform(),runtime,flow,new com.appnexus.printing.adapter.codec.JacksonPrintPayloadContent(),new com.appnexus.printing.adapter.persistence.MyBatisPrintOutputRepository(outputMapper),transaction);
    // 预检按模式检查设备；不创建任务，跨账号和跨租户都不泄露该单据。
    int jobsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM print_job",Integer.class);
    assertThat(outputs.preflight(design,documentId,"PREVIEW",null)).anySatisfy(check->{assertThat(check.layer()).isEqualTo("DEVICE");assertThat(check.status()).isEqualTo("NOT_APPLICABLE");});
    assertThat(outputs.preflight(design,documentId,"SILENT",deviceId)).allSatisfy(check->assertThat(check.status()).isEqualTo("READY"));
    jdbc.update("UPDATE print_agent_queue SET seen_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 121 SECOND) WHERE tenant_id=UUID_TO_BIN(?)",tenant);
    assertThat(outputs.preflight(design,documentId,"SILENT",deviceId)).anySatisfy(check->assertThat(check.reason()).isEqualTo("PRINT_DEVICE_OFFLINE"));
    flow.report(agent,new com.appnexus.printing.api.PrintData.QueueReport("default","agent-1",List.of("test-queue","中文队列")));
    jdbc.update("UPDATE print_device SET enabled=FALSE WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,deviceId);
    assertThat(outputs.preflight(design,documentId,"SILENT",deviceId)).anySatisfy(check->assertThat(check.reason()).isEqualTo("PRINT_DEVICE_UNAVAILABLE"));
    jdbc.update("UPDATE print_device SET enabled=TRUE WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,deviceId);
    assertThatThrownBy(()->outputs.preflight(new PrintContext(tenant,UUID.randomUUID().toString(),UUID.randomUUID().toString()),documentId,"PREVIEW",null)).hasMessage("PRINT_DOCUMENT_NOT_FOUND");
    assertThatThrownBy(()->outputs.preflight(new PrintContext(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString()),documentId,"PREVIEW",null)).hasMessage("PRINT_DOCUMENT_NOT_FOUND");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM print_job",Integer.class)).isEqualTo(jobsBefore);
    var bridge=new com.appnexus.printing.application.BusinessPrintingService(new com.appnexus.printing.adapter.persistence.MyBatisBusinessPrintQueries(businessMapper),outputs,(context,permission)->true);
    assertThat(bridge.rendererOnline(design)).isTrue();
    jdbc.update("UPDATE print_agent_queue SET seen_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 121 SECOND) WHERE tenant_id=UUID_TO_BIN(?)",tenant);
    assertThat(bridge.rendererOnline(design)).isFalse();
    flow.report(agent,new com.appnexus.printing.api.PrintData.QueueReport("default","agent-1",List.of("test-queue","中文队列")));
    assertThat(bridge.rendererOnline(design)).isTrue();
    assertThat(bridge.scenes(design,"warehouse.outbound.v1")).hasSize(1);
    assertThat(bridge.scenes(new PrintContext(UUID.randomUUID().toString(),design.actorId(),UUID.randomUUID().toString()),"warehouse.outbound.v1")).isEmpty();
    // 公开业务端口复用场景契约与幂等输出，数据先由业务端授权，打印端仍拒绝不兼容契约。
    var businessContext=new PrintContext(tenant,"partner-business-test",UUID.randomUUID().toString());
    var businessInput=new com.appnexus.contracts.printing.BusinessPrinting.Request("outbound","warehouse.outbound.v1","BUSINESS-1",Map.of("documentNumber","BUSINESS-1"),"PREVIEW",deviceId,1);
    assertThatThrownBy(()->bridge.request(businessContext,new com.appnexus.contracts.printing.BusinessPrinting.Request("outbound","wrong.v1","BUSINESS-1",Map.of(),"PREVIEW",deviceId,1))).hasMessage("PRINT_SCENE_UNAVAILABLE");
    // 后面的代理领取断言依赖队列顺序，业务端口的额外任务在本用例结束再创建。
    var external=new PrintContext(tenant,"partner-output-test",UUID.randomUUID().toString());
    var input=new com.appnexus.printing.api.PrintData.ExternalOutputRequest("outbound","OUT-EXT-001",mapper.readTree("{\"documentNumber\":\"OUT-EXT-001\"}"),"PREVIEW",deviceId,2,"warehouse.outbound.v1");
    var preview=outputs.external(external,input);String outputId=preview.get("id").toString();
    assertThat(outputs.external(external,input).get("id")).isEqualTo(outputId);
    assertThatThrownBy(()->outputs.external(external,new com.appnexus.printing.api.PrintData.ExternalOutputRequest("outbound","OTHER",input.data(),"PREVIEW",deviceId,2,"warehouse.outbound.v1"))).hasMessage("PRINT_IDEMPOTENCY_CONFLICT");
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
    var silent=outputs.external(new PrintContext(tenant,"partner-output-test",UUID.randomUUID().toString()),new com.appnexus.printing.api.PrintData.ExternalOutputRequest("outbound","OUT-SILENT",input.data(),"SILENT",deviceId,1,"warehouse.outbound.v1"));
    assertThat(silent.get("mode")).isEqualTo("SILENT");
    flow.saveScene(design,sceneId,new com.appnexus.printing.api.PrintData.SceneRequest("outbound","出仓单","warehouse.outbound.v1",templateId,1,false,0));
    assertThatThrownBy(()->flow.print(new PrintContext(tenant,actor,UUID.randomUUID().toString()),documentId,options)).hasMessage("PRINT_SCENE_UNAVAILABLE");
  }
  @Test void typedContractRejectsInvalidDataAndFreezesSchema(){
    String tenant=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString();var c=new PrintContext(tenant,actor,UUID.randomUUID().toString());
    var flow=new PrintSceneService(fixturePrintPlatform(),runtime,new com.appnexus.printing.adapter.codec.JacksonPrintSceneContent(),new com.appnexus.printing.adapter.persistence.MyBatisPrintSceneRepository(sceneMapper),transaction);
    var objectRuntime=new com.appnexus.contracts.businessobject.BusinessObjectRuntime(){
      public Snapshot read(com.appnexus.contracts.businessobject.BusinessObjects.Context context,String type,String id,String operation,long version,Set<String> fields){
        var record=jdbc.queryForMap("SELECT data_json,revision FROM print_document WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",context.tenantId(),id);
        @SuppressWarnings("unchecked") Map<String,Object> data=mapper.convertValue(mapper.readTree(record.get("data_json").toString()),Map.class);
        return new Snapshot(((Number)record.get("revision")).longValue(),null,data);
      }
      public Result execute(com.appnexus.contracts.businessobject.BusinessObjects.Context context,String type,String id,String operation,long version,Map<String,?> values){throw new UnsupportedOperationException("PRINT_ONLY_FIXTURE");}
    };
    com.appnexus.contracts.businessobject.BusinessObjects.Provider provider=new com.appnexus.contracts.businessobject.BusinessObjects.Provider(){
      public com.appnexus.contracts.businessobject.BusinessObjectRuntime runtime(){return objectRuntime;}
      public com.appnexus.contracts.businessobject.BusinessObjects.Descriptor descriptor(){return new com.appnexus.contracts.businessobject.BusinessObjects.Descriptor("TypedPreview","类型预览","Typed preview","test",1,com.appnexus.contracts.businessobject.BusinessObjects.Category.TRANSACTION,List.of(),List.of(
        new com.appnexus.contracts.businessobject.BusinessObjects.Operation("print","打印","Print","print.job:submit","TypedPreview.print",null,List.of(),new com.appnexus.contracts.businessobject.InteractionContract("TypedPreview.print",1,"TypedPreview","print","打印","Print",com.appnexus.contracts.businessobject.InteractionContract.Trigger.USER_ACTION,List.of(
          new com.appnexus.contracts.businessobject.InteractionContract.Capability(com.appnexus.contracts.businessobject.InteractionContract.Service.PRINT,com.appnexus.contracts.businessobject.InteractionContract.Completion.CONFIRMATION,"test.typed.v1","",List.of(new com.appnexus.contracts.businessobject.BusinessObjects.Field("days","天数","Days","NUMBER","天",true)),Set.of(),"test.typed.v1",1))))));}
    };
    flow.businessContracts(List.of(new com.appnexus.platform.bootstrap.businessobject.BusinessObjectConfiguration().interactionPrintContracts(List.of(provider))));
    var template=runtime.saveTemplate(c,null,new com.appnexus.printing.api.PrintData.TemplateDraft("typed","类型测试","A4","test.typed.v1",mapper.createObjectNode(),mapper.readTree("{\"panels\":[]}"),-1));
    String templateId=template.get("id").toString();runtime.publish(c,templateId,0);
    String scene=flow.saveScene(c,null,new com.appnexus.printing.api.PrintData.SceneRequest("typed","类型测试","test.typed.v1",templateId,1,true,-1)).getFirst().get("id").toString();
    assertThatThrownBy(()->flow.saveDocument(c,new com.appnexus.printing.api.PrintData.DocumentRequest(scene,"BAD",mapper.readTree("{\"days\":\"5\"}")))).hasMessage("DATA_FIELD_TYPE_INVALID");
    assertThatThrownBy(()->flow.saveDocument(c,new com.appnexus.printing.api.PrintData.DocumentRequest(scene,"MISSING",mapper.createObjectNode()))).hasMessage("DATA_FIELD_REQUIRED");
    String document=flow.saveDocument(c,new com.appnexus.printing.api.PrintData.DocumentRequest(scene,"GOOD",mapper.readTree("{\"days\":5}"))).getFirst().get("id").toString();
    var job=flow.print(new PrintContext(tenant,actor,UUID.randomUUID().toString()),document,new com.appnexus.printing.api.PrintData.DocumentPrintRequest(null,"PDF",1));
    assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(data_contract_schema,'$.code')) FROM print_job WHERE id=UUID_TO_BIN(?)",String.class,job.get("id"))).isEqualTo("test.typed.v1");
    // 使用真实打印公开适配器与SQL任务，不以mock的输出Map代替公共驱动闭环。
    var outputs=new PrintOutputService(fixturePrintPlatform(),runtime,flow,new com.appnexus.printing.adapter.codec.JacksonPrintPayloadContent(),new com.appnexus.printing.adapter.persistence.MyBatisPrintOutputRepository(outputMapper),transaction);
    var bridge=new com.appnexus.printing.application.BusinessPrintingService(new com.appnexus.printing.adapter.persistence.MyBatisBusinessPrintQueries(businessMapper),outputs,(context,permission)->true);
    var bindings=org.mockito.Mockito.mock(com.appnexus.contracts.businessobject.BusinessObjectBindings.class);
    org.mockito.Mockito.when(bindings.resolveOperation(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyMap())).thenReturn(
      new com.appnexus.contracts.businessobject.BusinessObjects.Resolution("native-resolution",tenant,"TypedPreview",document,0,1,null,0,Map.of(),"print","PRINT","typed","test.typed.v1"));
    var platform=org.mockito.Mockito.mock(com.appnexus.contracts.businessobject.BusinessObjectPlatform.class);
    org.mockito.Mockito.when(platform.active(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    org.mockito.Mockito.when(platform.authorizeRecord(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenAnswer(call->call.getArgument(3));
    org.mockito.Mockito.when(platform.operationAllowed(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("TypedPreview.print"),org.mockito.ArgumentMatchers.eq("print.job:submit"))).thenReturn(true);
    var executor=com.appnexus.platform.businessobject.BusinessObjectUseCases.execution(List.of(provider),bindings,new com.appnexus.platform.persistence.businessobject.configuration.SpringConfigurationTransaction(new org.springframework.jdbc.datasource.DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource()))),platform,()->null,()->bridge);
    var context=new com.appnexus.contracts.businessobject.BusinessObjects.Context(tenant,actor,UUID.randomUUID().toString());
    var request=new com.appnexus.contracts.businessobject.BusinessObjectExecution.Request("TypedPreview",document,"print","TypedPreview.print",0);
    var output=executor.execute(context,request).output();
    assertThat(output).containsEntry("mode","PREVIEW").containsEntry("deviceId",null).containsEntry("confirmedJobId",null);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM print_output_request WHERE id=UUID_TO_BIN(?)",Integer.class,output.get("id"))).isEqualTo(1);
    assertThat(executor.execute(context,request).output().get("id")).isEqualTo(output.get("id"));
    assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(data_snapshot,'$.days')) FROM print_job WHERE id=UUID_TO_BIN(?)",String.class,output.get("jobId"))).isEqualTo("5");

  }

  /** 测试用例复用生产事务许可，授权判断沿用已隔离的授权桩。 */
  private com.appnexus.contracts.printing.PrintPlatform fixturePrintPlatform(){
    return new com.appnexus.contracts.printing.PrintPlatform(){
      @Override public boolean allowed(PrintContext context,String permission){return true;}
      @Override public void authorizeRecordWrite(PrintContext context,String permission,String... targets){
        writes.authorizeServiceOperation(UUID.fromString(context.tenantId()),permission,
            List.of(targets),()->allowed(context,permission));
      }
      @Override public void authorizeAgentQueues(PrintContext context,String site,String agent){
        writes.authorizeServiceOperation(UUID.fromString(context.tenantId()),"print.agent:operate",
            List.of(context.actorId(),site,agent),()->allowed(context,"print.agent:operate"));
      }
    };
  }
}
