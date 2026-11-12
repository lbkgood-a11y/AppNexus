package com.appnexus.printing.adapter.jdbc;

import com.appnexus.contracts.printing.*;
import com.appnexus.printing.core.PrintRules;
import tools.jackson.databind.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 打印模板、不可变发布版本、设备和任务的事务边界；所有 SQL 显式绑定可信租户。 */
public class PrintRuntime {
  private final JdbcTemplate jdbc;
  private final PrintPlatform platform;
  private final PrintJson json;
  private final ObjectMapper mapper;

  public PrintRuntime(JdbcTemplate jdbc, PrintPlatform platform, ObjectMapper mapper) {
    this.jdbc = jdbc; this.platform = platform; this.mapper = mapper; this.json = new PrintJson(mapper);
  }

  /** 返回当前账号实际拥有的打印操作，用于前端改善体验；后端操作仍逐次复核。 */
  public List<String> capabilities(PrintContext c) {
    return Map.of("read","print.template:read","design","print.template:design","publish","print.template:publish",
        "devices","print.device:manage","jobs","print.job:read","submit","print.job:submit","reprint","print.job:reprint","agent","print.agent:operate")
        .entrySet().stream().filter(e -> platform.allowed(c,e.getValue())).map(Map.Entry::getKey).sorted().toList();
  }

  /** 当前租户设计人员可见的字段元数据，不返回任何真实业务值，不代替业务字段授权。 */
  public List<Map<String,Object>> designContracts(PrintContext c){require(c,"print.template:design");return PrintDesignCatalog.contracts();}

  public List<Map<String,Object>> templates(PrintContext c) {
    require(c,"print.template:read");
    return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,category,data_contract,scope_json,draft_json,draft_revision,published_version,enabled,created_at,updated_at FROM print_template WHERE tenant_id=UUID_TO_BIN(?) ORDER BY updated_at DESC",
        (rs,n) -> row(rs,"id","code","name","category","data_contract","scope_json","draft_json","draft_revision","published_version","enabled","created_at","updated_at"),c.tenantId());
  }

  @Transactional
  public Map<String,Object> saveTemplate(PrintContext c, String id, TemplateDraft d) {
    require(c,"print.template:design"); validateDraft(d);
    String template = json.template(d.template()); String scope = json.object(d.scope());
    UUID actor=uuid(c.actorId()), tenant=uuid(c.tenantId());
    if (id == null) {
      UUID created=UUID.randomUUID();
      jdbc.update("INSERT INTO print_template(tenant_id,id,code,name,category,data_contract,scope_json,draft_json,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),UUID_TO_BIN(?),UUID_TO_BIN(?))",
          tenant.toString(),created.toString(),d.code(),d.name().trim(),d.category(),d.dataContract(),scope,template,actor.toString(),actor.toString());
      id=created.toString();
    } else {
      uuid(id);
      int changed=jdbc.update("UPDATE print_template SET code=?,name=?,category=?,data_contract=?,scope_json=CAST(? AS JSON),draft_json=CAST(? AS JSON),draft_revision=draft_revision+1,updated_by=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND draft_revision=?",
          d.code(),d.name().trim(),d.category(),d.dataContract(),scope,template,actor.toString(),tenant.toString(),id,d.revision());
      if(changed==0) throw new PrintFailure("PRINT_TEMPLATE_CONFLICT",409);
    }
    return template(c,id);
  }

  public Map<String,Object> template(PrintContext c,String id) {
    require(c,"print.template:read"); uuid(id);
    return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,category,data_contract,scope_json,draft_json,draft_revision,published_version,enabled,created_at,updated_at FROM print_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
        rs -> { if(!rs.next()) throw new PrintFailure("PRINT_TEMPLATE_NOT_FOUND",404); return row(rs,"id","code","name","category","data_contract","scope_json","draft_json","draft_revision","published_version","enabled","created_at","updated_at");},c.tenantId(),id);
  }

  /**
   * 串行发布同一租户模板，固定草稿和版本号；行锁同时阻止保存草稿穿插版本快照。
   * @param c 可信租户与操作者上下文
   * @param id 模板 UUID
   * @param revision 调用方读取的草稿版本；不一致返回 409，不覆盖新草稿
   * @return 新发布版本对应的模板
   */
  @Transactional
  public Map<String,Object> publish(PrintContext c,String id,long revision) {
    require(c,"print.template:publish");
    uuid(id);
    jdbc.queryForList("SELECT id FROM print_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",c.tenantId(),id);
    Map<String,Object> current=templateWithPermission(c,id,"print.template:publish");
    if(((Number)current.get("draft_revision")).longValue()!=revision) throw new PrintFailure("PRINT_TEMPLATE_CONFLICT",409);
    int version=current.get("published_version")==null?1:((Number)current.get("published_version")).intValue()+1;
    jdbc.update("INSERT INTO print_template_version(tenant_id,template_id,version,template_json,scope_json,data_contract,published_by) SELECT tenant_id,id,?,draft_json,scope_json,data_contract,UUID_TO_BIN(?) FROM print_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",version,c.actorId(),c.tenantId(),id);
    jdbc.update("UPDATE print_template SET published_version=?,enabled=TRUE,updated_by=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",version,c.actorId(),c.tenantId(),id);
    return templateWithPermission(c,id,"print.template:publish");
  }

  public List<Map<String,Object>> versions(PrintContext c,String id) {
    require(c,"print.template:read"); uuid(id);
    return jdbc.query("SELECT version,template_json,scope_json,data_contract,BIN_TO_UUID(published_by) published_by,published_at FROM print_template_version WHERE tenant_id=UUID_TO_BIN(?) AND template_id=UUID_TO_BIN(?) ORDER BY version DESC",
        (rs,n)->row(rs,"version","template_json","scope_json","data_contract","published_by","published_at"),c.tenantId(),id);
  }

  /**
   * 将历史不可变版本复制回草稿；不覆盖历史或立即改变生产版本，发布仍需独立权限。
   * @param c 可信租户和设计人员上下文
   * @param id 当前租户模板 UUID
   * @param version 历史发布版本，从 1 开始
   * @param revision 当前草稿乐观锁版本，冲突返回 409
   * @return 新草稿，revision 增加 1
   */
  @Transactional
  public Map<String,Object> restoreVersion(PrintContext c,String id,int version,long revision){
    require(c,"print.template:design");uuid(id);
    if(version<1)throw new PrintFailure("PRINT_INPUT_INVALID",400);
    int changed=jdbc.update("UPDATE print_template t JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=? SET t.draft_json=v.template_json,t.scope_json=v.scope_json,t.data_contract=v.data_contract,t.draft_revision=t.draft_revision+1,t.updated_by=UUID_TO_BIN(?) WHERE t.tenant_id=UUID_TO_BIN(?) AND t.id=UUID_TO_BIN(?) AND t.draft_revision=?",version,c.actorId(),c.tenantId(),id,revision);
    if(changed==0)throw new PrintFailure("PRINT_TEMPLATE_CONFLICT",409);
    return templateWithPermission(c,id,"print.template:design");
  }

  public List<Map<String,Object>> devices(PrintContext c) {
    requireAny(c,"print.device:manage","print.job:submit");
    return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,site_code,system_name,capabilities_json,enabled,last_seen_at,created_at FROM print_device WHERE tenant_id=UUID_TO_BIN(?) ORDER BY name",
        (rs,n)->row(rs,"id","code","name","site_code","system_name","capabilities_json","enabled","last_seen_at","created_at"),c.tenantId());
  }

  @Transactional
  public Map<String,Object> saveDevice(PrintContext c,String id,DeviceDraft d) {
    require(c,"print.device:manage");
    if(!PrintRules.validCode(d.code())||!PrintRules.validCode(d.siteCode())||blank(d.name())||blank(d.systemName())) throw new PrintFailure("PRINT_INPUT_INVALID",400);
    String caps=json.object(d.capabilities()); UUID value=id==null?UUID.randomUUID():uuid(id);
    if(id==null) jdbc.update("INSERT INTO print_device(tenant_id,id,code,name,site_code,system_name,capabilities_json,created_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,CAST(? AS JSON),UUID_TO_BIN(?))",c.tenantId(),value.toString(),d.code(),d.name().trim(),d.siteCode(),d.systemName().trim(),caps,c.actorId());
    else if(jdbc.update("UPDATE print_device SET code=?,name=?,site_code=?,system_name=?,capabilities_json=CAST(? AS JSON),enabled=? WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",d.code(),d.name().trim(),d.siteCode(),d.systemName().trim(),caps,d.enabled(),c.tenantId(),id)==0) throw new PrintFailure("PRINT_DEVICE_NOT_FOUND",404);
    return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,site_code,system_name,capabilities_json,enabled,last_seen_at,created_at FROM print_device WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",rs->{rs.next();return row(rs,"id","code","name","site_code","system_name","capabilities_json","enabled","last_seen_at","created_at");},c.tenantId(),value.toString());
  }

  public List<Map<String,Object>> jobs(PrintContext c) {
    require(c,"print.job:read");
    return jdbc.query("SELECT BIN_TO_UUID(j.id) id,j.source_type,j.source_id,BIN_TO_UUID(j.template_id) template_id,j.template_version,j.output_type,BIN_TO_UUID(j.device_id) device_id,j.copies,j.state,j.attempt,j.result_json,BIN_TO_UUID(j.parent_job_id) parent_job_id,j.reprint_reason,j.created_at,j.updated_at,t.name template_name,d.name device_name FROM print_job j JOIN print_template t ON t.tenant_id=j.tenant_id AND t.id=j.template_id LEFT JOIN print_device d ON d.tenant_id=j.tenant_id AND d.id=j.device_id WHERE j.tenant_id=UUID_TO_BIN(?) ORDER BY j.created_at DESC LIMIT 500",
        (rs,n)->row(rs,"id","source_type","source_id","template_id","template_version","output_type","device_id","copies","state","attempt","result_json","parent_job_id","reprint_reason","created_at","updated_at","template_name","device_name"),c.tenantId());
  }

  @Transactional
  public Map<String,Object> submit(PrintContext c,JobDraft d) {
    require(c,"print.job:submit");
    // 外部机密主体不是人员账号；稳定 UUID 仅作任务审计键，权限仍从原始客户端身份复核。

    if(blank(d.sourceType())||blank(d.sourceId())||!PrintRules.validOutput(d.outputType())||d.copies()<1||d.copies()>99) throw new PrintFailure("PRINT_INPUT_INVALID",400);
    uuid(d.templateId()); if(d.deviceId()!=null) uuid(d.deviceId());
    // PDF 由任一认证渲染代理领取，不绑定打印机；实际出纸必须指定有效设备。
    if(d.outputType().equals("PRINTER")){
      if(d.deviceId()==null) throw new PrintFailure("PRINT_DEVICE_REQUIRED",400);
      Boolean enabled=jdbc.query("SELECT enabled FROM print_device WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",rs->{if(!rs.next())throw new PrintFailure("PRINT_DEVICE_NOT_FOUND",404);return rs.getBoolean(1);},c.tenantId(),d.deviceId());
      if(!Boolean.TRUE.equals(enabled))throw new PrintFailure("PRINT_DEVICE_DISABLED",409);
    }
    Map<String,Object> template=jdbc.query("SELECT published_version,enabled FROM print_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",rs->{if(!rs.next())throw new PrintFailure("PRINT_TEMPLATE_NOT_FOUND",404);return Map.of("version",rs.getInt(1),"enabled",rs.getBoolean(2));},c.tenantId(),d.templateId());
    int version=((Number)template.get("version")).intValue(); if(version<1||!((Boolean)template.get("enabled"))) throw new PrintFailure("PRINT_TEMPLATE_NOT_PUBLISHED",409);
    String snapshot=json.snapshot(d.data()); UUID id=UUID.randomUUID();
    try { jdbc.update("INSERT INTO print_job(tenant_id,id,request_id,actor_id,source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,state) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),?,CAST(? AS JSON),'QUEUED')",c.tenantId(),id.toString(),c.requestId(),actorKey(c),d.sourceType(),d.sourceId(),d.templateId(),version,d.outputType(),d.outputType().equals("PDF")?null:d.deviceId(),d.outputType().equals("PDF")?1:d.copies(),snapshot); }
    catch(DuplicateKeyException duplicate){ return byRequest(c); }
    return job(c,id.toString(),"print.job:submit");
  }

  @Transactional
  public Map<String,Object> reprint(PrintContext c,String id,String reason) {
    require(c,"print.job:reprint"); uuid(id); if(blank(reason)||reason.trim().length()>500) throw new PrintFailure("PRINT_REPRINT_REASON_REQUIRED",400);
    var existing=jdbc.queryForList("SELECT BIN_TO_UUID(id) id FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND actor_id=UUID_TO_BIN(?) AND request_id=UUID_TO_BIN(?)",c.tenantId(),c.actorId(),c.requestId());
    if(!existing.isEmpty())return job(c,existing.getFirst().get("id").toString(),"print.job:reprint");
    job(c,id,"print.job:reprint"); UUID next=UUID.randomUUID();
    jdbc.update("INSERT INTO print_job(tenant_id,id,request_id,actor_id,source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,state,parent_job_id,reprint_reason) SELECT tenant_id,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,'QUEUED',id,? FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",next.toString(),c.requestId(),c.actorId(),reason.trim(),c.tenantId(),id);
    return job(c,next.toString(),"print.job:reprint");
  }

  @Transactional
  public Optional<Map<String,Object>> claim(PrintContext c,String site,String agent) {
    require(c,"print.agent:operate"); if(!PrintRules.validCode(site)||blank(agent)||agent.length()>128) throw new PrintFailure("PRINT_INPUT_INVALID",400);
    // 代理失联时不能假定任务尚未进入系统队列；过期租约转为结果未知，必须人工判定后补打。
    jdbc.update("UPDATE print_job SET state='UNKNOWN',result_json=JSON_OBJECT('code','AGENT_LEASE_EXPIRED'),claim_expires_at=NULL WHERE tenant_id=UUID_TO_BIN(?) AND state='CLAIMED' AND claim_expires_at<CURRENT_TIMESTAMP(6)",c.tenantId());
    List<String> ids=jdbc.queryForList("SELECT BIN_TO_UUID(j.id) FROM print_job j LEFT JOIN print_device d ON d.tenant_id=j.tenant_id AND d.id=j.device_id WHERE j.tenant_id=UUID_TO_BIN(?) AND j.state='QUEUED' AND (j.output_type='PDF' OR (j.output_type='PRINTER' AND d.enabled=TRUE AND d.site_code=?)) ORDER BY j.created_at LIMIT 1 FOR UPDATE SKIP LOCKED",String.class,c.tenantId(),site);
    if(ids.isEmpty()) return Optional.empty(); String id=ids.getFirst();
    jdbc.update("UPDATE print_job SET state='CLAIMED',claimed_by=?,claim_expires_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 2 MINUTE),attempt=attempt+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",agent,c.tenantId(),id);
    jdbc.update("UPDATE print_device SET last_seen_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND site_code=?",c.tenantId(),site);
    return Optional.of(jdbc.query("SELECT BIN_TO_UUID(j.id) id,j.output_type,j.copies,j.data_snapshot,v.template_json,d.system_name,d.capabilities_json FROM print_job j JOIN print_template_version v ON v.tenant_id=j.tenant_id AND v.template_id=j.template_id AND v.version=j.template_version LEFT JOIN print_device d ON d.tenant_id=j.tenant_id AND d.id=j.device_id WHERE j.tenant_id=UUID_TO_BIN(?) AND j.id=UUID_TO_BIN(?)",rs->{rs.next();return row(rs,"id","output_type","copies","data_snapshot","template_json","system_name","capabilities_json");},c.tenantId(),id));
  }

  /**
   * 接收代理状态；同状态同结果可安全重放，终态及过期租约禁止被迟到回执覆盖。
   * @param c 可信租户上下文，必须具有专用代理权限
   * @param id 当前租户任务 UUID
   * @param receipt 领取代理、允许状态与非敏感结果；结果内容变化不能作为幂等重放
   * @return 更新后或幂等重放对应的原任务
   */
  @Transactional
  public Map<String,Object> receipt(PrintContext c,String id,Receipt receipt) {
    require(c,"print.agent:operate"); uuid(id);
    if(receipt==null||receipt.state()==null||!Set.of("SUBMITTED","SUCCEEDED","FAILED","UNKNOWN").contains(receipt.state())) throw new PrintFailure("PRINT_RECEIPT_STATE_INVALID",400);
    if(blank(receipt.agent())||receipt.agent().length()>128) throw new PrintFailure("PRINT_INPUT_INVALID",400);
    String result=json.object(receipt.result());
    var current=jdbc.query("SELECT state,claimed_by,result_json,(claim_expires_at<CURRENT_TIMESTAMP(6)) expired FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",
        rs->{if(!rs.next())throw new PrintFailure("PRINT_JOB_NOT_FOUND",404);return row(rs,"state","claimed_by","result_json","expired");},c.tenantId(),id);
    if(!receipt.agent().equals(current.get("claimed_by"))) throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
    String state=current.get("state").toString();
    if(state.equals(receipt.state())&&Objects.equals(current.get("result_json"),jsonNode(result))) return job(c,id,"print.agent:operate");
    // 未确认送入队列前不能直接声称纸张完成；已送入队列后的失败同样无法证明未出纸。
    boolean allowed=state.equals("CLAIMED")&&!Boolean.TRUE.equals(current.get("expired"))
        &&!(current.get("expired") instanceof Number n&&n.intValue()!=0)
        &&Set.of("SUBMITTED","FAILED","UNKNOWN").contains(receipt.state())
        ||state.equals("SUBMITTED")&&Set.of("SUCCEEDED","UNKNOWN").contains(receipt.state());
    if(!allowed) throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
    jdbc.update("UPDATE print_job SET state=?,result_json=CAST(? AS JSON),claim_expires_at=NULL WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",receipt.state(),result,c.tenantId(),id);
    return job(c,id,"print.agent:operate");
  }

  /** 反序列化已通过 PrintJson 校验的结果，仅用于比较 JSON 结构，忽略对象键顺序。 */
  private JsonNode jsonNode(String value){return mapper.readTree(value);}

  /**
   * 接收领取代理的 PDF，产物与任务成功状态在同一事务提交；不接受本地路径或任意 URL。
   * @param c 专用代理可信租户上下文
   * @param id PDF 任务 UUID
   * @param agent 当前领取代理标识
   * @param bytes PDF 原文，5 字节以上、最多 10 MiB，必须以 PDF 文件头开头
   * @return 成功任务；同一代理上传相同内容允许重放，内容改变返回冲突
   */
  @Transactional
  public Map<String,Object> uploadPdf(PrintContext c,String id,String agent,byte[] bytes) {
    require(c,"print.agent:operate");uuid(id);
    if(bytes==null||bytes.length<5||bytes.length>10*1024*1024
        ||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
      throw new PrintFailure("PRINT_PDF_INVALID",400);
    var current=jdbc.query("SELECT output_type,state,claimed_by,(claim_expires_at>CURRENT_TIMESTAMP(6)) active FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",
        rs->{if(!rs.next())throw new PrintFailure("PRINT_JOB_NOT_FOUND",404);return row(rs,"output_type","state","claimed_by","active");},c.tenantId(),id);
    if(!"PDF".equals(current.get("output_type"))||agent==null||!agent.equals(current.get("claimed_by")))
      throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
    if("SUCCEEDED".equals(current.get("state"))) {
      if(!Arrays.equals(bytes,downloadPdfBytes(c,id))) throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
      return job(c,id,"print.agent:operate");
    }
    Object active=current.get("active");
    if(!"CLAIMED".equals(current.get("state"))||!(Boolean.TRUE.equals(active)||active instanceof Number n&&n.intValue()==1))
      throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
    jdbc.update("INSERT INTO print_job_artifact(tenant_id,job_id,pdf_bytes) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?)",c.tenantId(),id,bytes);
    jdbc.update("UPDATE print_job SET state='SUCCEEDED',claim_expires_at=NULL,result_json=JSON_OBJECT('code','PDF_READY') WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId(),id);
    return job(c,id,"print.agent:operate");
  }

  /**
   * 下载当前租户 PDF；每次重新检查权限，不使用公开下载地址或客户端传入的存储路径。
   * @param c 当前人员会话上下文
   * @param id 当前租户任务 UUID
   * @return 原始 PDF 字节，控制器必须以附件和 no-store 返回
   */
  public byte[] downloadPdf(PrintContext c,String id){require(c,"print.job:read");uuid(id);return downloadPdfBytes(c,id);}

  /** 已通过请求归属校验的包内预览读取，外部客户端不能自行选择其他任务。 */
  byte[] outputPdf(PrintContext c,String id){return downloadPdfBytes(c,id);}

  /** 从预览任务复制固定版本和数据；确认输出不重新渲染可变化业务数据，禁止修改快照。 */
  Map<String,Object> printPreview(PrintContext c,String id,String device,int copies){
    require(c,"print.job:submit");uuid(device);
    if(jdbc.queryForObject("SELECT COUNT(*) FROM print_device WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",Integer.class,c.tenantId(),device)!=1)throw new PrintFailure("PRINT_DEVICE_DISABLED",409);
    String next=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO print_job(tenant_id,id,request_id,actor_id,source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,state,parent_job_id) SELECT tenant_id,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),source_type,source_id,template_id,template_version,'PRINTER',UUID_TO_BIN(?),?,data_snapshot,'QUEUED',id FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND output_type='PDF' AND state='SUCCEEDED'",next,UUID.randomUUID().toString(),actorKey(c),device,copies,c.tenantId(),id);
    return job(c,next,"print.job:submit");
  }

  /** 内部读取已经过身份校验的任务产物；不存在与跨租户均返回相同 404。 */
  private byte[] downloadPdfBytes(PrintContext c,String id){return jdbc.query("SELECT pdf_bytes FROM print_job_artifact WHERE tenant_id=UUID_TO_BIN(?) AND job_id=UUID_TO_BIN(?)",rs->{if(!rs.next())throw new PrintFailure("PRINT_PDF_NOT_FOUND",404);return rs.getBytes(1);},c.tenantId(),id);}

  private Map<String,Object> byRequest(PrintContext c){String id=jdbc.query("SELECT BIN_TO_UUID(id) id FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND actor_id=UUID_TO_BIN(?) AND request_id=UUID_TO_BIN(?)",rs->{if(!rs.next())throw new PrintFailure("PRINT_IDEMPOTENCY_CONFLICT",409);return rs.getString(1);},c.tenantId(),actorKey(c),c.requestId());return job(c,id,"print.job:submit");}
  private Map<String,Object> job(PrintContext c,String id,String permission){require(c,permission);return jdbc.query("SELECT BIN_TO_UUID(id) id,source_type,source_id,BIN_TO_UUID(template_id) template_id,template_version,output_type,BIN_TO_UUID(device_id) device_id,copies,data_snapshot,state,attempt,result_json,BIN_TO_UUID(parent_job_id) parent_job_id,reprint_reason,created_at,updated_at FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",rs->{if(!rs.next())throw new PrintFailure("PRINT_JOB_NOT_FOUND",404);return row(rs,"id","source_type","source_id","template_id","template_version","output_type","device_id","copies","data_snapshot","state","attempt","result_json","parent_job_id","reprint_reason","created_at","updated_at");},c.tenantId(),id);}
  private Map<String,Object> templateWithPermission(PrintContext c,String id,String permission){require(c,permission);uuid(id);return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,category,data_contract,scope_json,draft_json,draft_revision,published_version,enabled,created_at,updated_at FROM print_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",rs->{if(!rs.next())throw new PrintFailure("PRINT_TEMPLATE_NOT_FOUND",404);return row(rs,"id","code","name","category","data_contract","scope_json","draft_json","draft_revision","published_version","enabled","created_at","updated_at");},c.tenantId(),id);}
  private void validateDraft(TemplateDraft d){if(d==null||!PrintRules.validCode(d.code())||!PrintRules.validCode(d.dataContract())||blank(d.name())||d.name().trim().length()>128||!Set.of("A4","LABEL","WAYBILL","RECEIPT").contains(d.category())||d.revision() < -1)throw new PrintFailure("PRINT_INPUT_INVALID",400);}
  private void require(PrintContext c,String permission){if(!platform.allowed(c,permission))throw new PrintFailure("PRINT_ACCESS_DENIED",403);}
  private void requireAny(PrintContext c,String... permissions){if(Arrays.stream(permissions).noneMatch(p->platform.allowed(c,p)))throw new PrintFailure("PRINT_ACCESS_DENIED",403);}
  private static String actorKey(PrintContext c){return c.actorId().startsWith("partner-")?UUID.nameUUIDFromBytes(c.actorId().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString():c.actorId();}
  private static UUID uuid(String value){try{return UUID.fromString(value);}catch(Exception e){throw new PrintFailure("PRINT_IDENTIFIER_INVALID",400);}}
  private static boolean blank(String value){return value==null||value.isBlank();}
  private Map<String,Object> row(ResultSet rs,String... names)throws SQLException{Map<String,Object> result=new LinkedHashMap<>();for(String name:names){Object v=rs.getObject(name);if(v instanceof Timestamp t)v=t.toInstant();if(v instanceof String s&&(name.endsWith("_json")||name.equals("data_snapshot")))try{v=mapper.readTree(s);}catch(Exception ignored){v=null;}result.put(name,v);}return result;}

  /** 模板草稿；revision 为读取到的乐观锁版本，新建传 -1。 */
  public record TemplateDraft(String code,String name,String category,String dataContract,JsonNode scope,JsonNode template,long revision) {}
  /** 设备草稿；enabled 缺省由调用方传 true，能力 JSON 不得包含凭据。 */
  public record DeviceDraft(String code,String name,String siteCode,String systemName,JsonNode capabilities,boolean enabled) {}
  /** 正式输出任务；data 必须是业务服务按 PRINT 用途授权后的快照。 */
  public record JobDraft(String sourceType,String sourceId,String templateId,String outputType,String deviceId,int copies,JsonNode data) {}
  /** 代理回执；结果只允许非敏感诊断码，不上传文档内容或系统日志。 */
  public record Receipt(String state,String agent,JsonNode result) {}
}
