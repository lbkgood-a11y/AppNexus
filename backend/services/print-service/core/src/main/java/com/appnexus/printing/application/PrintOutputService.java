package com.appnexus.printing.application;


import com.appnexus.contracts.printing.*;
import java.util.*;
import com.appnexus.printing.api.PrintData.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;




/** 统一输出编排：静默直接入队，预览必须显式确认；外部主体仅能读取和确认自己的请求。 */
public class PrintOutputService implements com.appnexus.printing.api.PrintOutputManagement {
 private final PrintOutputRepository statements;
 private final PrintTransaction transaction;

  /** 进程内业务提供器入口；不映射为人员任意快照 API，远程入口仍要求外部打印 scope。 */
   public Map<String,Object> business(PrintContext c,BusinessPrinting.Request input){return transaction.required(()->{
    require(c,"print.job:submit");validate(input.mode(),input.copies());
    var rows=statements.businessScene(c.tenantId(),input.sceneCode(),input.dataContract());
    if(rows.isEmpty())throw new PrintRejection("PRINT_SCENE_UNAVAILABLE",409);
    var data=input.data();
    return create(c,input.mode(),input.deviceId(),input.copies(),input,()->flow.submitSnapshot(c,rows.getFirst().get("id").toString(),input.sourceId(),data,input.mode().equals("SILENT")?"PRINTER":"PDF",input.deviceId(),input.copies()));
  });}
  
  /**
   * 本人打印单据的输出预检；不创建输出、任务或租约，PDF/预览不要求物理设备。
   * @param c 可信租户及当前账号
   * @param documentId 本人已保存单据 UUID，无权对象按不存在处理
   * @param mode PREVIEW 或 SILENT；前者仅需要渲染依赖，后者还需目标队列
   * @param deviceId SILENT 必填的本租户设备 UUID，PREVIEW 可省略
   * @return 场景/模板、渲染节点及物理设备分层结果，不含业务正文或其他人的设备信息
   */
  public List<TargetCheck> preflight(PrintContext c,String documentId,String mode,String deviceId){
    require(c,"print.job:submit");if(!Set.of("PREVIEW","SILENT").contains(mode))throw new PrintRejection("PRINT_INPUT_INVALID",400);
    UUID.fromString(documentId);
    var documents=statements.ownDocument(c.tenantId(),documentId,c.actorId());
    if(documents.isEmpty())throw new PrintRejection("PRINT_DOCUMENT_NOT_FOUND",404);
    var checks=new ArrayList<TargetCheck>();
    boolean scene=Boolean.TRUE.equals(statements.sceneReady(c.tenantId(),(String)documents.getFirst().get("scene")));
    checks.add(new TargetCheck("SCENE_TEMPLATE",scene?"READY":"BLOCKED",scene?"READY":"PRINT_TARGET_INVALID"));
    if(!scene){checks.add(new TargetCheck("RENDERER","NOT_CHECKED","UPSTREAM_BLOCKED"));checks.add(new TargetCheck("DEVICE","NOT_CHECKED","UPSTREAM_BLOCKED"));return List.copyOf(checks);}
    boolean renderer=Boolean.TRUE.equals(statements.rendererReady(c.tenantId()));
    checks.add(new TargetCheck("RENDERER",renderer?"READY":"BLOCKED",renderer?"READY":"PRINT_AGENT_OFFLINE"));
    if(mode.equals("PREVIEW"))checks.add(new TargetCheck("DEVICE","NOT_APPLICABLE","PREVIEW_NO_DEVICE_REQUIRED"));
    else if(deviceId==null||deviceId.isBlank())checks.add(new TargetCheck("DEVICE","BLOCKED","PRINT_DEVICE_REQUIRED"));
    else{
      UUID.fromString(deviceId);
      var devices=statements.enabledDevice(c.tenantId(),deviceId);
      if(devices.isEmpty())checks.add(new TargetCheck("DEVICE","BLOCKED","PRINT_DEVICE_UNAVAILABLE"));
      else{var device=devices.getFirst();boolean online=Boolean.TRUE.equals(statements.deviceQueueOnline(c.tenantId(),(String)device.get("site_code"),(String)device.get("system_name")));checks.add(new TargetCheck("DEVICE",online?"READY":"BLOCKED",online?"READY":"PRINT_DEVICE_OFFLINE"));}
    }
    return List.copyOf(checks);
  }

 private final PrintPlatform platform;private final PrintPreviewOutput runtime;private final PrintSubmission flow;private final PrintPayloadContent content;
  public PrintOutputService(PrintPlatform platform,PrintPreviewOutput runtime,PrintSubmission flow,PrintPayloadContent content,PrintOutputRepository statements,PrintTransaction transaction){this.transaction=transaction;this.statements=statements;this.platform=platform;this.runtime=runtime;this.flow=flow;this.content=content;}

  /**
   * 外部系统以场景编码和契约快照调用；专用 scope 表示调用方获准提供该租户打印数据。
   * @param c 认证链建立的固定租户和客户端，不接受客户端传入租户或操作者
   * @param input mode 为 SILENT/PREVIEW，sceneCode 为启用场景编码；copies 为 1—99
   * @return 请求 ID、任务状态和统一预览链接，不创建公开访问凭据
   */
  
  public Map<String,Object> external(PrintContext c,ExternalOutputRequest input){return transaction.required(()->{
    require(c,"print.external:submit");validate(input.mode(),input.copies());
    var rows=statements.externalScene(c.tenantId(),input.sceneCode());
    if(rows.isEmpty())throw new PrintRejection("PRINT_SCENE_UNAVAILABLE",409);
    if(input.dataContract()==null||!input.dataContract().equals(rows.getFirst().get("data_contract")))throw new PrintRejection("PRINT_CONTRACT_INVALID",409);
    return create(c,input.mode(),input.deviceId(),input.copies(),input,()->flow.submitSnapshot(internal(c),rows.getFirst().get("id").toString(),input.sourceId(),input.data(),input.mode().equals("SILENT")?"PRINTER":"PDF",input.deviceId(),input.copies()));
  });}

  /** 人员业务入口仅提交本人已保存单据 ID；PREVIEW 不会将任务送入实体队列。 */
  
  public Map<String,Object> document(PrintContext c,String id,DocumentOutputRequest input){return transaction.required(()->{
    require(c,"print.job:submit");validate(input.mode(),input.copies());
    return create(c,input.mode(),input.deviceId(),input.copies(),Map.of("document",id,"options",input),()->flow.print(c,id,new com.appnexus.printing.api.PrintData.DocumentPrintRequest(input.deviceId(),input.mode().equals("SILENT")?"PRINTER":"PDF",input.copies())));
  });}

  private Map<String,Object> create(PrintContext c,String mode,String device,int copies,Object payload,java.util.function.Supplier<Map<String,Object>> submit){
    String hash=hash(content.encode(payload));
    // 同租户请求串行去重，先查请求再创建任务，网络重试不产生额外出纸。
    statements.lockTenantOutputs(c.tenantId());
    var previous=statements.existingRequest(c.tenantId(),c.actorId(),uuid(c.requestId()));
    if(!previous.isEmpty()){if(!hash.equals(previous.getFirst().get("payload_hash")))throw new PrintRejection("PRINT_IDEMPOTENCY_CONFLICT",409);return status(c,previous.getFirst().get("id").toString());}
    var job=submit.get();String id=UUID.randomUUID().toString();
    platform.authorizeRecordWrite(c,"print.job:submit",id,c.actorId(),(String)job.get("id"));
    statements.createOutput(c.tenantId(),id,c.actorId(),c.requestId(),hash,mode,(String)job.get("id"),device==null?null:uuid(device),copies);
    return status(c,id);
  }

  /** 当前会话有任务读取权限时可审核本租户外部请求；外部客户端只能访问自己请求。 */
  public Map<String,Object> status(PrintContext c,String id){
    access(c);var rows=statements.outputStatus(c.tenantId(),uuid(id));
    if(rows.isEmpty()||c.actorId().startsWith("partner-")&&!c.actorId().equals(rows.getFirst().get("owner")))throw new PrintRejection("PRINT_OUTPUT_NOT_FOUND",404);
    var value=rows.getFirst();value.remove("owner");
    boolean online=Boolean.TRUE.equals(statements.rendererOnline(c.tenantId()));
    value.put("rendererOnline",online);
    if(value.get("confirmedJobId")!=null){var job=statements.confirmedJob(c.tenantId(),(String)value.get("confirmedJobId"));value.put("deliveryState",job.get("state"));}
    value.put("previewUrl","/#/print/management?output="+id);return value;
  }

  /** 统一预览 PDF 附件仅通过当前认证上下文访问，不在 URL 中放 Token。 */
  public byte[] pdf(PrintContext c,String id){var request=status(c,id);return runtime.outputPdf(internal(c),request.get("jobId").toString());}

  /**
   * 确认精确预览快照；行锁与 confirmed_job_id 保证重复点击只生成一项实际打印任务。
   * @param device 目标启用设备 UUID；null 沿用创建时设备
   */
  
  public Map<String,Object> confirm(PrintContext c,String id,String device){return transaction.required(()->{
    require(c,c.actorId().startsWith("partner-")?"print.external:submit":"print.job:submit");
    status(c,id);statements.lockOutput(c.tenantId(),id);
    var request=status(c,id);if(request.get("confirmedJobId")!=null)return request;
    if(!"PREVIEW".equals(request.get("mode"))||!"SUCCEEDED".equals(request.get("state")))throw new PrintRejection("PRINT_PREVIEW_NOT_READY",409);
    String selected=device==null?(String)request.get("deviceId"):uuid(device);if(selected==null)throw new PrintRejection("PRINT_DEVICE_REQUIRED",400);
    var job=runtime.printPreview(internal(c),request.get("jobId").toString(),selected,((Number)request.get("copies")).intValue());
    platform.authorizeRecordWrite(c,"print.job:submit",id,(String)job.get("id"));
    statements.confirmOutput((String)job.get("id"),c.tenantId(),id);return status(c,id);
  });}
  private void access(PrintContext c){require(c,c.actorId().startsWith("partner-")?"print.external:submit":"print.job:read");}
  private void require(PrintContext c,String p){if(!platform.allowed(c,p))throw new PrintRejection("PRINT_ACCESS_DENIED",403);}
  private PrintContext internal(PrintContext c){return c;}
  private void validate(String mode,int copies){if(mode==null||!Set.of("SILENT","PREVIEW").contains(mode)||copies<1||copies>99)throw new PrintRejection("PRINT_INPUT_INVALID",400);}
  private static String uuid(String s){try{return UUID.fromString(s).toString();}catch(Exception e){throw new PrintRejection("PRINT_IDENTIFIER_INVALID",400);}}
  private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  
  
}
