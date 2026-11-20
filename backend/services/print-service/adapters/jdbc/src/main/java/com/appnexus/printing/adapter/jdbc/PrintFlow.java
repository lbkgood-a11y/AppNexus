package com.appnexus.printing.adapter.jdbc;
import com.appnexus.printing.adapter.jdbc.PrintFlowSqlMapper;

import com.appnexus.contracts.printing.*;
import com.appnexus.printing.core.PrintRules;
import tools.jackson.databind.*;
import java.util.*;

import org.springframework.transaction.annotation.Transactional;

/** 配置式打印闭环；手工单据读取仅限本人，场景配置需发布权限，代理队列不自动登记设备。 */
public class PrintFlow {
 private final PrintFlowSqlMapper statements;

  private List<Map<String,Object>> businessContracts=List.of();
  /** 构建登记业务契约，与自定义租户契约使用同一格式及校验，不访问业务数据。 */
  public void businessContracts(List<PrintDataContractProvider> providers){
    var values=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();PrintDesignCatalog.contracts().forEach(c->keys.add(c.get("code").toString()));
    for(var provider:providers)for(var contract:provider.contracts()){if(!keys.add(contract.get("code").toString()))throw new IllegalArgumentException("PRINT_CONTRACT_DUPLICATE");values.add(Map.copyOf(contract));}businessContracts=List.copyOf(values);
  }
  
  private final PrintPlatform platform;
  private final PrintRuntime runtime;
  private final ObjectMapper mapper;
  private final PrintJson json;
  public PrintFlow(PrintPlatform platform,PrintRuntime runtime,ObjectMapper mapper,PrintFlowSqlMapper statements){this.statements=statements;this.platform=platform;this.runtime=runtime;this.mapper=mapper;this.json=new PrintJson(mapper);runtime.dataContracts(this::contract,this::validateData);}

  /** 返回授权人员可用的场景；只返回元数据，不读取其他人的业务单据。 */
  public List<Map<String,Object>> scenes(PrintContext c){allow(c,"print.template:read","print.job:submit","print.template:publish");return statements.statement1(c.tenantId());}

  /**
   * 保存场景配置并校验模板当前发布契约；编码创建后不可修改，配置更新不改变已创建任务。
   * @param c 可信人员上下文，需模板发布权限
   * @param id 新建为空，更新为当前租户场景 UUID
   * @param d 编码为 1—64 位字母开头的字母、数字、下划线、点或连字符，租户内唯一；场景名、契约、模板、默认份数和乐观锁版本；份数为 1—99
   * @return 保存后的场景目录
   * @throws PrintFailure 无发布权限、编码不合法、试图修改已保存编码、模板不兼容或版本冲突时拒绝保存
   */
  @Transactional
  public List<Map<String,Object>> saveScene(PrintContext c,String id,Scene d){
    allow(c,"print.template:publish");
    if(d==null||!PrintRules.validCode(d.code())||d.name()==null||d.name().isBlank()||d.name().length()>128||d.copies()<1||d.copies()>99)bad();
    // 场景编码是外部系统的稳定入口；行锁保护检查与保存，不能仅靠页面只读防止改码。
    if(id!=null){
      var existing=statements.statement2(c.tenantId(),uuid(id));
      if(existing.isEmpty())throw new PrintFailure("PRINT_SCENE_CONFLICT",409);
      if(!d.code().equals(existing.getFirst().get("code")))throw new PrintFailure("PRINT_SCENE_CODE_IMMUTABLE",409);
    }
    contract(c,d.contract());
    var templates=statements.statement3(c.tenantId(),uuid(d.templateId()));
    if(templates.isEmpty()||!d.contract().equals(templates.getFirst().get("data_contract")))throw new PrintFailure("PRINT_SCENE_TEMPLATE_INVALID",409);
    if(id==null)statements.statement4(c.tenantId(),UUID.randomUUID().toString(),d.code(),d.name().trim(),d.contract(),d.templateId(),d.copies(),d.enabled(),c.actorId());
    else if(statements.statement5(d.code(),d.name().trim(),d.contract(),uuid(d.templateId()),d.copies(),d.enabled(),c.actorId(),c.tenantId(),uuid(id),d.revision())==0)throw new PrintFailure("PRINT_SCENE_CONFLICT",409);
    return scenes(c);
  }

  /** 返回当前操作者在当前租户保存的单据；不通过打印权限扩大其他业务单据读取范围。 */
  public List<Map<String,Object>> documents(PrintContext c){allow(c,"print.job:submit");var rows=statements.statement6(c.tenantId(),c.actorId());for(var row:rows)row.put("data_json",mapper.readTree(row.get("data_json").toString()));return rows;}

  /** 按契约白名单保存真实录入单据；不从客户端接受租户、账号或任意数据源 URL。 */
  @Transactional
  public List<Map<String,Object>> saveDocument(PrintContext c,Document d){
    allow(c,"print.job:submit");
    if(d==null||d.number()==null||d.number().isBlank()||d.number().length()>128)bad();
    var scene=scene(c,d.sceneId());
    if(d.data()!=null&&d.data().has("documentNumber")&&!d.number().trim().equals(d.data().get("documentNumber").asText()))throw new PrintFailure("PRINT_INPUT_INVALID",400);
    validateData(d.data(),contract(c,scene.get("data_contract").toString()));
    statements.statement7(c.tenantId(),UUID.randomUUID().toString(),d.sceneId(),d.number().trim(),json.snapshot(d.data()),c.actorId());
    return documents(c);
  }

  /**
   * 仅凭服务端单据 ID 创建任务；固定契约、发布模板和数据快照，浏览器不能替换打印字段。
   * @param c 当前人员上下文及幂等键
   * @param id 本人手工单据 UUID
   * @param d 用户选定设备、PDF/PRINTER 渠道及 1—99 份数
   * @return 已持久化的任务，网络失败后同一幂等键可重放
   */
  @Transactional
  public Map<String,Object> print(PrintContext c,String id,Output d){
    allow(c,"print.job:submit");
    var docs=statements.statement8(c.tenantId(),uuid(id),c.actorId());
    if(docs.isEmpty())throw new PrintFailure("PRINT_BUSINESS_OBJECT_NOT_FOUND",404);
    var doc=docs.getFirst();var scene=scene(c,doc.get("scene_id").toString());
    var template=statements.statement9(c.tenantId(),scene.get("template_id"));
    if(template.isEmpty()||!scene.get("data_contract").equals(template.getFirst().get("data_contract")))throw new PrintFailure("PRINT_SCENE_TEMPLATE_INVALID",409);
    return runtime.submit(c,new PrintRuntime.JobDraft("CONFIGURED_DOCUMENT",id,scene.get("template_id").toString(),d.outputType(),d.deviceId(),d.copies(),mapper.readTree(doc.get("data_json").toString())));
  }

  /** 包内外部编排入口：先由 PrintOutputs 验证专用权限，再按场景校验契约；不开放任意模板调用。 */
  Map<String,Object> submitSnapshot(PrintContext c,String sceneId,String sourceId,JsonNode data,String output,String device,int copies){
    var scene=scene(c,sceneId);if(sourceId==null||sourceId.isBlank()||sourceId.length()>128)bad();
    validateData(data,contract(c,scene.get("data_contract").toString()));
    var versions=statements.statement10(c.tenantId(),scene.get("template_id"));
    if(versions.isEmpty()||!scene.get("data_contract").equals(versions.getFirst().get("data_contract")))throw new PrintFailure("PRINT_SCENE_TEMPLATE_INVALID",409);
    return runtime.submit(c,new PrintRuntime.JobDraft("EXTERNAL_DOCUMENT",sourceId,scene.get("template_id").toString(),output,device,copies,data));
  }

  /** 契约目录可供设计者和录入人员查看；仅提供字段元数据与合成样例。 */
  public List<Map<String,Object>> contracts(PrintContext c){
    allow(c,"print.template:design","print.job:submit","print.template:publish","print.external:submit","print.template:read");
    var values=new ArrayList<Map<String,Object>>(PrintDesignCatalog.contracts());
    values.addAll(businessContracts);
    for(String definition:statements.statement11(c.tenantId())){
      JsonNode node=mapper.readTree(definition);Map<String,Object> value=new LinkedHashMap<>();
      value.put("code",node.get("code").textValue());value.put("nameZh",node.get("nameZh").textValue());value.put("nameEn",node.get("nameEn").textValue());value.put("designOnly",false);
      value.put("fields",mapper.convertValue(node.get("fields"),List.class));values.add(value);
    }
    return values;
  }

  /**
   * 配置租户字段契约；只增不改，防止已有发布模板因字段重命名失效。
   * @param c 当前设计人员可信上下文
   * @param definition 名称、带版本编码和最多 50 个文本/明细字段；不接受脚本、URL 数据源或 SQL
   * @return 新契约目录；编码冲突由控制器返回 409
   */
  @Transactional
  public List<Map<String,Object>> createContract(PrintContext c,JsonNode definition){
    allow(c,"print.template:design");
    if(definition==null||!definition.isObject()||!PrintRules.validCode(definition.path("code").asText())
        ||PrintDesignCatalog.contracts().stream().anyMatch(value->value.get("code").equals(definition.path("code").asText())))bad();
    for(String label:List.of("nameZh","nameEn"))if(!definition.path(label).isTextual()||definition.path(label).asText().isBlank()||definition.path(label).asText().length()>128)bad();
    validateFields(definition.path("fields"),true);
    // 只持久化白名单元数据，丢弃客户端额外键，防止未知配置被未来渲染路径解释。
    var clean=mapper.createObjectNode().put("code",definition.get("code").asText()).put("nameZh",definition.get("nameZh").asText()).put("nameEn",definition.get("nameEn").asText());
    clean.set("fields",definition.get("fields"));
    statements.statement12(c.tenantId(),definition.get("code").asText(),json.object(clean),c.actorId());
    return contracts(c);
  }

  /** 字段键限制为直接标识符，拒绝路径表达式与原型键；明细不允许再嵌套明细。 */
  private void validateFields(JsonNode fields,boolean arrays){
    if(!fields.isArray()||fields.isEmpty()||fields.size()>50)bad();
    Set<String> keys=new HashSet<>();
    for(JsonNode field:fields){
      String key=field.path("key").asText();
      if(!key.matches("[A-Za-z][A-Za-z0-9_]{0,63}")||Set.of("constructor","prototype","__proto__").contains(key)||!keys.add(key))bad();
      for(String label:List.of("labelZh","labelEn"))if(!field.path(label).isTextual()||field.path(label).asText().isBlank()||field.path(label).asText().length()>128)bad();
      if(!field.path("sample").isTextual()||field.path("sample").asText().length()>2000)bad();
      if(field.has("columns")){if(!arrays)bad();validateFields(field.get("columns"),false);}
    }
  }

  /** 客户端上报队列仅更新自身站点和实例范围；最大 100 个队列，名称不能包含控制字符。 */
  @Transactional
  public void report(PrintContext c,QueueReport d){
    allow(c,"print.agent:operate");
    if(d==null||!PrintRules.validCode(d.site())||d.agent()==null||d.agent().isBlank()||d.agent().length()>128||d.queues()==null||d.queues().size()>100)bad();
    for(String name:d.queues())if(name==null||name.isBlank()||name.length()>255||name.chars().anyMatch(Character::isISOControl))bad();
    statements.statement13(c.tenantId(),d.site(),d.agent());
    for(String name:new LinkedHashSet<>(d.queues()))statements.statement14(c.tenantId(),d.site(),d.agent(),name);
  }

  /** 只列出最近 120 秒认证上报的客户端队列，登记设备仍需独立设备管理权限。 */
  public List<Map<String,Object>> queues(PrintContext c){allow(c,"print.device:manage");return statements.statement15(c.tenantId());}

  private Map<String,Object> scene(PrintContext c,String id){var rows=statements.statement16(c.tenantId(),uuid(id));if(rows.isEmpty())throw new PrintFailure("PRINT_SCENE_UNAVAILABLE",409);return rows.getFirst();}
  private Map<String,Object> contract(PrintContext c,String code){return contracts(c).stream().filter(value->value.get("code").equals(code)).findFirst().orElseThrow(()->new PrintFailure("PRINT_CONTRACT_INVALID",400));}

  /** 仅接受契约内文本和明细，最多 500 行、单元格 2000 字符；不执行表达式或任意字段路径。 */
  @SuppressWarnings("unchecked")
  private void validateData(JsonNode data,Map<String,Object> definition){
    if(data==null||!data.isObject())bad();
    var fields=(List<Map<String,Object>>)definition.get("fields");
    // 业务投影具有严格类型；历史租户文本/明细契约保留原协议，不能隐式改变既有模板。
    if(fields.stream().allMatch(f->f.containsKey("type"))){
      var schema=new com.appnexus.contracts.data.DataContract(definition.get("code").toString(),((Number)definition.getOrDefault("version",1)).intValue(),"PRINT",fields.stream().map(f->new com.appnexus.contracts.data.DataContract.Field(f.get("key").toString(),com.appnexus.contracts.data.DataContract.Type.valueOf(f.get("type").toString()),Boolean.TRUE.equals(f.get("required")),Objects.toString(f.get("unit"),""))).toList());
      schema.validate(mapper.convertValue(data,Map.class));return;
    }
    for(var property:data.properties()){
      var field=fields.stream().filter(value->value.get("key").equals(property.getKey())).findFirst().orElseThrow(()->new PrintFailure("PRINT_CONTRACT_INVALID",400));
      JsonNode value=property.getValue();
      if(field.containsKey("columns")){
        if(!value.isArray()||value.size()>500)bad();
        for(JsonNode row:value)validateData(row,Map.of("fields",field.get("columns")));
      }else if(!value.isTextual()||value.textValue().length()>2000)bad();
    }
  }
  private void allow(PrintContext c,String... permissions){if(Arrays.stream(permissions).noneMatch(p->platform.allowed(c,p)))throw new PrintFailure("PRINT_ACCESS_DENIED",403);}
  private static String uuid(String value){try{return UUID.fromString(value).toString();}catch(Exception e){throw new PrintFailure("PRINT_IDENTIFIER_INVALID",400);}}
  private static void bad(){throw new PrintFailure("PRINT_INPUT_INVALID",400);}
  /** 场景参数；revision 新建可传 -1，更新必须使用读取值，copies 默认由页面设 1。 */
  public record Scene(String code,String name,String contract,String templateId,int copies,boolean enabled,long revision){}
  /** 用户录入单据；data 仅允许已注册字段，number 是业务人员填写的显示编号。 */
  public record Document(String sceneId,String number,JsonNode data){}
  /** 打印选项；设备为当前租户已授权设备，outputType 仅 PDF/PRINTER，copies 为 1—99。 */
  public record Output(String deviceId,String outputType,int copies){}
  /** 认证代理上报；站点、实例稳定标识和实际系统队列名，不包含设备凭据。 */
  public record QueueReport(String site,String agent,List<String> queues){}
}
