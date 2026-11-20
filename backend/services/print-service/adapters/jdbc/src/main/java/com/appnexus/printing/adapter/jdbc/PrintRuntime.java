package com.appnexus.printing.adapter.jdbc;
import com.appnexus.printing.adapter.jdbc.PrintRuntimeSqlMapper;

import com.appnexus.contracts.printing.*;
import com.appnexus.printing.core.PrintRules;
import tools.jackson.databind.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;

import org.springframework.transaction.annotation.Transactional;

/** 打印模板、不可变发布版本、设备和任务的事务边界；所有 SQL 显式绑定可信租户。 */
public class PrintRuntime {
 private final PrintRuntimeSqlMapper statements;

  /** 代理连接诊断元数据，身份与固定租户由平台权威端口提供，不返回密钥。 */
  public Map<String,String> agentIdentity(PrintContext c){require(c,"print.agent:operate");return platform.agentIdentity(c);}
  private java.util.function.BiFunction<PrintContext,String,Map<String,Object>> contractCatalog;
  private java.util.function.BiConsumer<JsonNode,Map<String,Object>> contractValidator;
  /** 复用打印目录与校验器；装配不取业务数据，防止原始任务入口绕过字段校验。 */
  void dataContracts(java.util.function.BiFunction<PrintContext,String,Map<String,Object>> catalog,java.util.function.BiConsumer<JsonNode,Map<String,Object>> validator){this.contractCatalog=catalog;this.contractValidator=validator;}
  /** 递归检查模板 field 引用；明细列由父契约提供，不接受未知字段。 */
  private void validateReferences(JsonNode node,Set<String> fields){
    if(node.isObject())for(var property:node.properties()){
      if(property.getKey().equals("field")&&property.getValue().isTextual()&&!property.getValue().asText().isBlank()&&!fields.contains(property.getValue().asText()))throw new com.appnexus.contracts.data.DataContract.Violation(property.getValue().asText(),"DATA_FIELD_UNKNOWN");
      validateReferences(property.getValue(),fields);
    }else if(node.isArray())for(var child:node)validateReferences(child,fields);
  }
  @SuppressWarnings("unchecked")
  private void collectFields(List<Map<String,Object>> fields,Set<String> keys){for(var field:fields){keys.add(field.get("key").toString());if(field.containsKey("columns"))collectFields((List<Map<String,Object>>)field.get("columns"),keys);}}

  
  private final PrintPlatform platform;
  private final PrintJson json;
  private final ObjectMapper mapper;
  private final com.appnexus.printing.application.PrintReceiptService receipts;

  /**
   * 装配现有打印技术入口及回执纯应用用例，不在应用中构造技术客户端。
   * @param jdbc 与宿主事务管理器共用数据源，回执锁和写入必须使用同一事务连接
   * @param platform 可信打印授权端口，每次用例执行重新校验
   * @param mapper 既有 JSON 协议编解码器，回执比较使用结构相等，不依赖键顺序
   */
  public PrintRuntime( PrintPlatform platform, ObjectMapper mapper,PrintReceiptMapper receiptMapper,PrintRuntimeSqlMapper statements) {this.statements=statements;
     this.platform = platform; this.mapper = mapper; this.json = new PrintJson(mapper);
    this.receipts = new com.appnexus.printing.application.PrintReceiptService(new MyBatisPrintReceiptStore(receiptMapper,mapper),platform);
  }



  /** 当前租户设计人员可见的字段元数据，不返回任何真实业务值，不代替业务字段授权。 */
  public List<Map<String,Object>> designContracts(PrintContext c){require(c,"print.template:design");return PrintDesignCatalog.contracts();}

  public List<Map<String,Object>> templates(PrintContext c) {
    require(c,"print.template:read");
    return statements.read0(c.tenantId()).stream().map(this::projectRow).toList();
  }

  @Transactional
  public Map<String,Object> saveTemplate(PrintContext c, String id, TemplateDraft d) {
    var service=new com.appnexus.printing.application.TemplateEditingService(new com.appnexus.printing.application.TemplateEditingPort(){
      private String template,scope;
      @Override public void prepare(){template=json.template(d.template());scope=json.object(d.scope());}
      @Override public String identifier(String value){return uuid(value).toString();}
      @Override public void create(PrintContext context,String target,Draft input){
        statements.statement1(context.tenantId(),target,input.code(),input.name().trim(),input.category(),input.contract(),scope,template,context.actorId(),context.actorId());
      }
      @Override public int update(PrintContext context,String target,Draft input){
        return statements.statement2(input.code(),input.name().trim(),input.category(),input.contract(),scope,template,context.actorId(),context.tenantId(),target,input.revision());
      }
      @Override public Map<String,Object> result(PrintContext context,String target){return template(context,target);}
    },platform);
    var input=d==null?null:new com.appnexus.printing.application.TemplateEditingPort.Draft(d.code(),d.name(),d.category(),d.dataContract(),d.revision());
    try{return service.save(c,id,input);}
    catch(com.appnexus.contracts.printing.PrintRejection error){throw new PrintFailure(error.code(),error.status());}
  }

  public Map<String,Object> template(PrintContext c,String id) {
    require(c,"print.template:read"); uuid(id);
    return projectRow(required(statements.read1(c.tenantId(),id),"PRINT_TEMPLATE_NOT_FOUND",404));
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
    var service=new com.appnexus.printing.application.TemplatePublicationService(new com.appnexus.printing.application.TemplatePublicationPort(){
      private Map<String,Object> current;
      @Override public Draft lock(PrintContext context,String template){
        statements.statement3(context.tenantId(),template);
        current=templateWithPermission(context,template,"print.template:publish");
        return new Draft(((Number)current.get("draft_revision")).longValue(),current.get("published_version")==null?null:((Number)current.get("published_version")).intValue(),current.get("data_contract").toString());
      }
      @Override public String validatedSchema(PrintContext context,String template,String contract){
        Map<String,Object> schema=contractCatalog==null?null:contractCatalog.apply(context,contract);
        if(schema!=null){var fields=new HashSet<String>();collectFields((List<Map<String,Object>>)schema.get("fields"),fields);validateReferences(mapper.readTree(current.get("draft_json").toString()),fields);}
        return schema==null?null:mapper.writeValueAsString(schema);
      }
      @Override public void publish(PrintContext context,String template,int version,String schema){
        statements.statement4(version,context.actorId(),schema,context.tenantId(),template);
        statements.statement5(version,context.actorId(),context.tenantId(),template);
      }
      @Override public Map<String,Object> result(PrintContext context,String template){return templateWithPermission(context,template,"print.template:publish");}
    },platform);
    try{return service.publish(c,id,revision);}
    catch(com.appnexus.contracts.printing.PrintRejection error){throw new PrintFailure(error.code(),error.status());}
  }

  public List<Map<String,Object>> versions(PrintContext c,String id) {
    require(c,"print.template:read"); uuid(id);
    return statements.read2(c.tenantId(),id).stream().map(this::projectRow).toList();
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
    int changed=statements.statement6(version,c.actorId(),c.tenantId(),id,revision);
    if(changed==0)throw new PrintFailure("PRINT_TEMPLATE_CONFLICT",409);
    return templateWithPermission(c,id,"print.template:design");
  }

  public List<Map<String,Object>> devices(PrintContext c) {
    requireAny(c,"print.device:manage","print.job:submit");
    return statements.read3(c.tenantId()).stream().map(this::projectRow).toList();
  }

  @Transactional
  public Map<String,Object> saveDevice(PrintContext c,String id,DeviceDraft d) {
    require(c,"print.device:manage");
    if(!PrintRules.validCode(d.code())||!PrintRules.validCode(d.siteCode())||blank(d.name())||blank(d.systemName())) throw new PrintFailure("PRINT_INPUT_INVALID",400);
    String caps=json.object(d.capabilities()); UUID value=id==null?UUID.randomUUID():uuid(id);
    if(id==null) statements.statement7(c.tenantId(),value.toString(),d.code(),d.name().trim(),d.siteCode(),d.systemName().trim(),caps,c.actorId());
    else if(statements.statement8(d.code(),d.name().trim(),d.siteCode(),d.systemName().trim(),caps,d.enabled(),c.tenantId(),id)==0) throw new PrintFailure("PRINT_DEVICE_NOT_FOUND",404);
    return projectRow(required(statements.read4(c.tenantId(),value.toString()),"PRINT_TEMPLATE_NOT_FOUND",404));
  }

  public List<Map<String,Object>> jobs(PrintContext c) {
    require(c,"print.job:read");
    return statements.read5(c.tenantId()).stream().map(this::projectRow).toList();
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
      Boolean enabled=Boolean.TRUE.equals(required(statements.read6(c.tenantId(),d.deviceId()),"PRINT_DEVICE_NOT_FOUND",404).get("enabled"));
      if(!Boolean.TRUE.equals(enabled))throw new PrintFailure("PRINT_DEVICE_DISABLED",409);
    }
    Map<String,Object> template=required(statements.read7(c.tenantId(),d.templateId()),"PRINT_TEMPLATE_NOT_FOUND",404);
    int version=((Number)template.get("version")).intValue(); if(version<1||!((Boolean)template.get("enabled"))) throw new PrintFailure("PRINT_TEMPLATE_NOT_PUBLISHED",409);
    var published=statements.statement9(c.tenantId(),d.templateId(),version);
    Map<String,Object> schema=published.get("data_contract_schema")==null?(contractCatalog==null?null:contractCatalog.apply(c,published.get("data_contract").toString())):mapper.convertValue(mapper.readTree(published.get("data_contract_schema").toString()),Map.class);
    // 新发布业务模板固定完整字段语义；目录变更后需重新审查发布，不能静默套用新结构。
    if(schema!=null&&schema.containsKey("version")&&contractCatalog!=null&&!schema.equals(contractCatalog.apply(c,published.get("data_contract").toString())))throw new PrintFailure("DATA_CONTRACT_CHANGED",409);
    if(schema!=null&&contractValidator!=null)contractValidator.accept(d.data(),schema);
    String snapshot=json.snapshot(d.data()); UUID id=UUID.randomUUID();
    try { statements.statement10(c.tenantId(),id.toString(),c.requestId(),actorKey(c),d.sourceType(),d.sourceId(),d.templateId(),version,d.outputType(),d.outputType().equals("PDF")?null:d.deviceId(),d.outputType().equals("PDF")?1:d.copies(),snapshot,schema==null?null:mapper.writeValueAsString(schema)); }
    catch(DuplicateKeyException duplicate){ return byRequest(c); }
    return job(c,id.toString(),"print.job:submit");
  }

  @Transactional
  public Map<String,Object> reprint(PrintContext c,String id,String reason) {
    require(c,"print.job:reprint"); uuid(id); if(blank(reason)||reason.trim().length()>500) throw new PrintFailure("PRINT_REPRINT_REASON_REQUIRED",400);
    var existing=statements.statement11(c.tenantId(),c.actorId(),c.requestId());
    if(!existing.isEmpty())return job(c,existing.getFirst().get("id").toString(),"print.job:reprint");
    job(c,id,"print.job:reprint"); UUID next=UUID.randomUUID();
    statements.statement12(next.toString(),c.requestId(),c.actorId(),reason.trim(),c.tenantId(),id);
    return job(c,next.toString(),"print.job:reprint");
  }

  @Transactional
  public Optional<Map<String,Object>> claim(PrintContext c,String site,String agent) {
    require(c,"print.agent:operate"); if(!PrintRules.validCode(site)||blank(agent)||agent.length()>128) throw new PrintFailure("PRINT_INPUT_INVALID",400);
    // 代理失联时不能假定任务尚未进入系统队列；过期租约转为结果未知，必须人工判定后补打。
    statements.statement13(c.tenantId());
    List<String> ids=statements.statement14(c.tenantId(),site);
    if(ids.isEmpty()) return Optional.empty(); String id=ids.getFirst();
    statements.statement15(agent,c.tenantId(),id);
    statements.statement16(c.tenantId(),site);
    return Optional.of(projectRow(required(statements.read8(c.tenantId(),id),"PRINT_TEMPLATE_NOT_FOUND",404)));
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
    // 事务入站装饰器包裹整个应用用例及结果读取；不得自调用绕过 Spring 代理。
    try{receipts.accept(c,id,receipt==null?null:receipt.state(),receipt==null?null:receipt.agent(),()->json.object(receipt.result()));}
    catch(com.appnexus.contracts.printing.PrintRejection error){throw new PrintFailure(error.code(),error.status());}
    return job(c,id,"print.agent:operate");
  }

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
    var current=projectRow(required(statements.read9(c.tenantId(),id),"PRINT_JOB_NOT_FOUND",404));
    if(!"PDF".equals(current.get("output_type"))||agent==null||!agent.equals(current.get("claimed_by")))
      throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
    if("SUCCEEDED".equals(current.get("state"))) {
      if(!Arrays.equals(bytes,downloadPdfBytes(c,id))) throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
      return job(c,id,"print.agent:operate");
    }
    Object active=current.get("active");
    if(!"CLAIMED".equals(current.get("state"))||!(Boolean.TRUE.equals(active)||active instanceof Number n&&n.intValue()==1))
      throw new PrintFailure("PRINT_JOB_RECEIPT_CONFLICT",409);
    statements.statement17(c.tenantId(),id,bytes);
    statements.statement18(c.tenantId(),id);
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
    if(statements.statement19(c.tenantId(),device)!=1)throw new PrintFailure("PRINT_DEVICE_DISABLED",409);
    String next=UUID.randomUUID().toString();
    statements.statement20(next,UUID.randomUUID().toString(),actorKey(c),device,copies,c.tenantId(),id);
    return job(c,next,"print.job:submit");
  }

  /** 内部读取已经过身份校验的任务产物；不存在与跨租户均返回相同 404。 */
  private byte[] downloadPdfBytes(PrintContext c,String id){return (byte[])required(statements.read10(c.tenantId(),id),"PRINT_PDF_NOT_FOUND",404).get("pdf_bytes");}

  private Map<String,Object> byRequest(PrintContext c){String id=(String)required(statements.read11(c.tenantId(),actorKey(c),c.requestId()),"PRINT_IDEMPOTENCY_CONFLICT",409).get("id");return job(c,id,"print.job:submit");}
  private Map<String,Object> job(PrintContext c,String id,String permission){require(c,permission);return projectRow(required(statements.read12(c.tenantId(),id),"PRINT_JOB_NOT_FOUND",404));}
  private Map<String,Object> templateWithPermission(PrintContext c,String id,String permission){require(c,permission);uuid(id);return projectRow(required(statements.read13(c.tenantId(),id),"PRINT_TEMPLATE_NOT_FOUND",404));}
  private void require(PrintContext c,String permission){if(!platform.allowed(c,permission))throw new PrintFailure("PRINT_ACCESS_DENIED",403);}
  private void requireAny(PrintContext c,String... permissions){if(Arrays.stream(permissions).noneMatch(p->platform.allowed(c,p)))throw new PrintFailure("PRINT_ACCESS_DENIED",403);}
  private static String actorKey(PrintContext c){return c.actorId().startsWith("partner-")?UUID.nameUUIDFromBytes(c.actorId().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString():c.actorId();}
  private static UUID uuid(String value){try{return UUID.fromString(value);}catch(Exception e){throw new PrintFailure("PRINT_IDENTIFIER_INVALID",400);}}
  private static boolean blank(String value){return value==null||value.isBlank();}
  /** 固定Mapper结果沿用原UTC和JSON响应契约，失败JSON仍不当作有效结果。 */
  private Map<String,Object> projectRow(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();row.forEach((name,original)->{Object value=original;if(value instanceof Timestamp time)value=time.toInstant();if(value instanceof String text&&(name.endsWith("_json")||name.equals("data_snapshot")))try{value=mapper.readTree(text);}catch(Exception ignored){value=null;}result.put(name,value);});return result;}
  private Map<String,Object> required(List<Map<String,Object>> rows,String code,int status){if(rows.isEmpty())throw new PrintFailure(code,status);return rows.getFirst();}

  /** 模板草稿；revision 为读取到的乐观锁版本，新建传 -1。 */
  public record TemplateDraft(String code,String name,String category,String dataContract,JsonNode scope,JsonNode template,long revision) {}
  /** 设备草稿；enabled 缺省由调用方传 true，能力 JSON 不得包含凭据。 */
  public record DeviceDraft(String code,String name,String siteCode,String systemName,JsonNode capabilities,boolean enabled) {}
  /** 正式输出任务；data 必须是业务服务按 PRINT 用途授权后的快照。 */
  public record JobDraft(String sourceType,String sourceId,String templateId,String outputType,String deviceId,int copies,JsonNode data) {}
  /** 代理回执；结果只允许非敏感诊断码，不上传文档内容或系统日志。 */
  public record Receipt(String state,String agent,JsonNode result) {}
}
