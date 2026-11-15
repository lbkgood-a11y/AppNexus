package com.appnexus.printing.adapter.jdbc;

import com.appnexus.contracts.printing.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

/** 统一输出编排：静默直接入队，预览必须显式确认；外部主体仅能读取和确认自己的请求。 */
public class PrintOutputs {
  /** 进程内业务提供器入口；不映射为人员任意快照 API，远程入口仍要求外部打印 scope。 */
  @Transactional public Map<String,Object> business(PrintContext c,BusinessPrinting.Request input){
    require(c,"print.job:submit");validate(input.mode(),input.copies());
    var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id FROM print_scene WHERE tenant_id=UUID_TO_BIN(?) AND code=? AND data_contract=? AND enabled=TRUE",c.tenantId(),input.sceneCode(),input.dataContract());
    if(rows.isEmpty())throw new PrintFailure("PRINT_SCENE_UNAVAILABLE",409);
    var data=mapper.valueToTree(input.data());
    return create(c,input.mode(),input.deviceId(),input.copies(),input,()->flow.submitSnapshot(c,rows.getFirst().get("id").toString(),input.sourceId(),data,input.mode().equals("SILENT")?"PRINTER":"PDF",input.deviceId(),input.copies()));
  }
  /** 分层预检事实；瞬时离线不等于没有权限，也不保证后续任务执行成功。 */
  public record TargetCheck(String layer,String status,String reason) {}
  /**
   * 本人打印单据的输出预检；不创建输出、任务或租约，PDF/预览不要求物理设备。
   * @param c 可信租户及当前账号
   * @param documentId 本人已保存单据 UUID，无权对象按不存在处理
   * @param mode PREVIEW 或 SILENT；前者仅需要渲染依赖，后者还需目标队列
   * @param deviceId SILENT 必填的本租户设备 UUID，PREVIEW 可省略
   * @return 场景/模板、渲染节点及物理设备分层结果，不含业务正文或其他人的设备信息
   */
  public List<TargetCheck> preflight(PrintContext c,String documentId,String mode,String deviceId){
    require(c,"print.job:submit");if(!Set.of("PREVIEW","SILENT").contains(mode))throw new PrintFailure("PRINT_INPUT_INVALID",400);
    UUID.fromString(documentId);
    var documents=jdbc.queryForList("SELECT BIN_TO_UUID(scene_id) scene FROM print_document WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND created_by=UUID_TO_BIN(?)",c.tenantId(),documentId,c.actorId());
    if(documents.isEmpty())throw new PrintFailure("PRINT_DOCUMENT_NOT_FOUND",404);
    var checks=new ArrayList<TargetCheck>();
    boolean scene=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM print_scene s JOIN print_template t ON t.tenant_id=s.tenant_id AND t.id=s.template_id JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE s.tenant_id=UUID_TO_BIN(?) AND s.id=UUID_TO_BIN(?) AND s.enabled=TRUE AND t.enabled=TRUE AND v.data_contract=s.data_contract)",Boolean.class,c.tenantId(),documents.getFirst().get("scene")));
    checks.add(new TargetCheck("SCENE_TEMPLATE",scene?"READY":"BLOCKED",scene?"READY":"PRINT_TARGET_INVALID"));
    if(!scene){checks.add(new TargetCheck("RENDERER","NOT_CHECKED","UPSTREAM_BLOCKED"));checks.add(new TargetCheck("DEVICE","NOT_CHECKED","UPSTREAM_BLOCKED"));return List.copyOf(checks);}
    boolean renderer=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(?) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))",Boolean.class,c.tenantId()));
    checks.add(new TargetCheck("RENDERER",renderer?"READY":"BLOCKED",renderer?"READY":"PRINT_AGENT_OFFLINE"));
    if(mode.equals("PREVIEW"))checks.add(new TargetCheck("DEVICE","NOT_APPLICABLE","PREVIEW_NO_DEVICE_REQUIRED"));
    else if(deviceId==null||deviceId.isBlank())checks.add(new TargetCheck("DEVICE","BLOCKED","PRINT_DEVICE_REQUIRED"));
    else{
      UUID.fromString(deviceId);
      var devices=jdbc.queryForList("SELECT site_code,system_name FROM print_device WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",c.tenantId(),deviceId);
      if(devices.isEmpty())checks.add(new TargetCheck("DEVICE","BLOCKED","PRINT_DEVICE_UNAVAILABLE"));
      else{var device=devices.getFirst();boolean online=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(?) AND site_code=? AND queue_name=? AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))",Boolean.class,c.tenantId(),device.get("site_code"),device.get("system_name")));checks.add(new TargetCheck("DEVICE",online?"READY":"BLOCKED",online?"READY":"PRINT_DEVICE_OFFLINE"));}
    }
    return List.copyOf(checks);
  }

  private final JdbcTemplate jdbc;private final PrintPlatform platform;private final PrintRuntime runtime;private final PrintFlow flow;private final ObjectMapper mapper;
  public PrintOutputs(JdbcTemplate jdbc,PrintPlatform platform,PrintRuntime runtime,PrintFlow flow,ObjectMapper mapper){this.jdbc=jdbc;this.platform=platform;this.runtime=runtime;this.flow=flow;this.mapper=mapper;}

  /**
   * 外部系统以场景编码和契约快照调用；专用 scope 表示调用方获准提供该租户打印数据。
   * @param c 认证链建立的固定租户和客户端，不接受客户端传入租户或操作者
   * @param input mode 为 SILENT/PREVIEW，sceneCode 为启用场景编码；copies 为 1—99
   * @return 请求 ID、任务状态和统一预览链接，不创建公开访问凭据
   */
  @Transactional
  public Map<String,Object> external(PrintContext c,Input input){
    require(c,"print.external:submit");validate(input.mode(),input.copies());
    var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,data_contract FROM print_scene WHERE tenant_id=UUID_TO_BIN(?) AND code=? AND enabled=TRUE",c.tenantId(),input.sceneCode());
    if(rows.isEmpty())throw new PrintFailure("PRINT_SCENE_UNAVAILABLE",409);
    if(input.dataContract()==null||!input.dataContract().equals(rows.getFirst().get("data_contract")))throw new PrintFailure("PRINT_CONTRACT_INVALID",409);
    return create(c,input.mode(),input.deviceId(),input.copies(),input,()->flow.submitSnapshot(internal(c),rows.getFirst().get("id").toString(),input.sourceId(),input.data(),input.mode().equals("SILENT")?"PRINTER":"PDF",input.deviceId(),input.copies()));
  }

  /** 人员业务入口仅提交本人已保存单据 ID；PREVIEW 不会将任务送入实体队列。 */
  @Transactional
  public Map<String,Object> document(PrintContext c,String id,DocumentInput input){
    require(c,"print.job:submit");validate(input.mode(),input.copies());
    return create(c,input.mode(),input.deviceId(),input.copies(),Map.of("document",id,"options",input),()->flow.print(c,id,new PrintFlow.Output(input.deviceId(),input.mode().equals("SILENT")?"PRINTER":"PDF",input.copies())));
  }

  private Map<String,Object> create(PrintContext c,String mode,String device,int copies,Object payload,java.util.function.Supplier<Map<String,Object>> submit){
    String hash=hash(mapper.writeValueAsString(payload));
    // 同租户请求串行去重，先查请求再创建任务，网络重试不产生额外出纸。
    jdbc.queryForList("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(?) FOR UPDATE",c.tenantId());
    var previous=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,payload_hash FROM print_output_request WHERE tenant_id=UUID_TO_BIN(?) AND owner=? AND request_id=UUID_TO_BIN(?)",c.tenantId(),c.actorId(),uuid(c.requestId()));
    if(!previous.isEmpty()){if(!hash.equals(previous.getFirst().get("payload_hash")))throw new PrintFailure("PRINT_IDEMPOTENCY_CONFLICT",409);return status(c,previous.getFirst().get("id").toString());}
    var job=submit.get();String id=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO print_output_request(tenant_id,id,owner,request_id,payload_hash,mode,job_id,device_id,copies) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),?)",c.tenantId(),id,c.actorId(),c.requestId(),hash,mode,job.get("id"),device==null?null:uuid(device),copies);
    return status(c,id);
  }

  /** 当前会话有任务读取权限时可审核本租户外部请求；外部客户端只能访问自己请求。 */
  public Map<String,Object> status(PrintContext c,String id){
    access(c);var rows=jdbc.queryForList("SELECT BIN_TO_UUID(r.id) id,r.owner,r.mode,BIN_TO_UUID(r.job_id) jobId,BIN_TO_UUID(r.confirmed_job_id) confirmedJobId,BIN_TO_UUID(r.device_id) deviceId,r.copies,j.state,EXISTS(SELECT 1 FROM print_job_artifact a WHERE a.tenant_id=r.tenant_id AND a.job_id=r.job_id) pdfReady FROM print_output_request r JOIN print_job j ON j.tenant_id=r.tenant_id AND j.id=r.job_id WHERE r.tenant_id=UUID_TO_BIN(?) AND r.id=UUID_TO_BIN(?)",c.tenantId(),uuid(id));
    if(rows.isEmpty()||c.actorId().startsWith("partner-")&&!c.actorId().equals(rows.getFirst().get("owner")))throw new PrintFailure("PRINT_OUTPUT_NOT_FOUND",404);
    var value=rows.getFirst();value.remove("owner");
    boolean online=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(?) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))",Boolean.class,c.tenantId()));
    value.put("rendererOnline",online);
    if(value.get("confirmedJobId")!=null){var job=jdbc.queryForMap("SELECT state FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId(),value.get("confirmedJobId"));value.put("deliveryState",job.get("state"));}
    value.put("previewUrl","/#/print/management?output="+id);return value;
  }

  /** 统一预览 PDF 附件仅通过当前认证上下文访问，不在 URL 中放 Token。 */
  public byte[] pdf(PrintContext c,String id){var request=status(c,id);return runtime.outputPdf(internal(c),request.get("jobId").toString());}

  /**
   * 确认精确预览快照；行锁与 confirmed_job_id 保证重复点击只生成一项实际打印任务。
   * @param device 目标启用设备 UUID；null 沿用创建时设备
   */
  @Transactional
  public Map<String,Object> confirm(PrintContext c,String id,String device){
    require(c,c.actorId().startsWith("partner-")?"print.external:submit":"print.job:submit");
    status(c,id);jdbc.queryForList("SELECT id FROM print_output_request WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",c.tenantId(),id);
    var request=status(c,id);if(request.get("confirmedJobId")!=null)return request;
    if(!"PREVIEW".equals(request.get("mode"))||!"SUCCEEDED".equals(request.get("state")))throw new PrintFailure("PRINT_PREVIEW_NOT_READY",409);
    String selected=device==null?(String)request.get("deviceId"):uuid(device);if(selected==null)throw new PrintFailure("PRINT_DEVICE_REQUIRED",400);
    var job=runtime.printPreview(internal(c),request.get("jobId").toString(),selected,((Number)request.get("copies")).intValue());
    jdbc.update("UPDATE print_output_request SET confirmed_job_id=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",job.get("id"),c.tenantId(),id);return status(c,id);
  }
  private void access(PrintContext c){require(c,c.actorId().startsWith("partner-")?"print.external:submit":"print.job:read");}
  private void require(PrintContext c,String p){if(!platform.allowed(c,p))throw new PrintFailure("PRINT_ACCESS_DENIED",403);}
  private PrintContext internal(PrintContext c){return c;}
  private void validate(String mode,int copies){if(mode==null||!Set.of("SILENT","PREVIEW").contains(mode)||copies<1||copies>99)throw new PrintFailure("PRINT_INPUT_INVALID",400);}
  private static String uuid(String s){try{return UUID.fromString(s).toString();}catch(Exception e){throw new PrintFailure("PRINT_IDENTIFIER_INVALID",400);}}
  private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  /** 外部参数；sourceId 为调用方单据号，data 必须符合已注册契约，mode 必填。 */
  public record Input(String sceneCode,String sourceId,JsonNode data,String mode,String deviceId,int copies,String dataContract) {}
  /** 人员入口不接受业务快照，mode 为 SILENT 或 PREVIEW。 */
  public record DocumentInput(String mode,String deviceId,int copies){}
}
