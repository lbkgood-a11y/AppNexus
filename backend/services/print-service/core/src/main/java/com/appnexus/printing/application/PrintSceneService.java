package com.appnexus.printing.application;
import com.appnexus.printing.core.PrintDataPolicy;
import com.appnexus.printing.application.PrintOutputService;
import com.appnexus.printing.core.design.PrintDesignCatalog;


import com.appnexus.contracts.printing.*;
import com.appnexus.printing.core.PrintRules;

import java.util.*;
import com.appnexus.printing.api.PrintData.*;



/** 配置式打印闭环；手工单据读取仅限本人，场景配置需发布权限，代理队列不自动登记设备。 */
public class PrintSceneService implements com.appnexus.printing.api.PrintSceneManagement, com.appnexus.printing.application.PrintSubmission {
 private final PrintSceneRepository statements;

  private List<Map<String,Object>> businessContracts=List.of();
  /** 构建登记业务契约，与自定义租户契约使用同一格式及校验，不访问业务数据。 */
  public void businessContracts(List<PrintDataContractProvider> providers){
    var values=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();PrintDesignCatalog.contracts().forEach(c->keys.add(c.get("code").toString()));
    for(var provider:providers)for(var contract:provider.contracts()){if(!keys.add(contract.get("code").toString()))throw new IllegalArgumentException("PRINT_CONTRACT_DUPLICATE");values.add(Map.copyOf(contract));}businessContracts=List.copyOf(values);
  }
  
  private final PrintPlatform platform;
  private final PrintSceneRuntime runtime;
  private final PrintSceneContent content;
  private final PrintTransaction transaction;

  /** @param platform 权威打印权限契约 @param runtime 原统一任务运行端口 @param content 固定内容编解码 @param statements 固定场景仓储 @param transaction 同数据源完整事务 */
  public PrintSceneService(PrintPlatform platform,PrintSceneRuntime runtime,PrintSceneContent content,PrintSceneRepository statements,PrintTransaction transaction){this.statements=statements;this.platform=platform;this.runtime=runtime;this.content=content;this.transaction=transaction;runtime.dataContracts(this::contract,this::validateData);}

  /** 返回授权人员可用的场景；只返回元数据，不读取其他人的业务单据。 */
  public List<Map<String,Object>> scenes(PrintContext c){allow(c,SCENE_READ_PERMISSIONS.toArray(String[]::new));return statements.scenes(c.tenantId());}

  /**
   * 保存场景配置并校验模板当前发布契约；编码创建后不可修改，配置更新不改变已创建任务。
   * @param c 可信人员上下文，需模板发布权限
   * @param id 新建为空，更新为当前租户场景 UUID
   * @param d 编码为 1—64 位字母开头的字母、数字、下划线、点或连字符，租户内唯一；场景名、契约、模板、默认份数和乐观锁版本；份数为 1—99
   * @return 保存后的场景目录
   * @throws PrintRejection 无发布权限、编码不合法、试图修改已保存编码、模板不兼容或版本冲突时拒绝保存
   */
  
  public List<Map<String,Object>> saveScene(PrintContext c,String id,SceneRequest d){return transaction.required(()->{
    allow(c,"print.template:publish");
    if(d==null||!PrintRules.validCode(d.code())||d.name()==null||d.name().isBlank()||d.name().length()>128||d.copies()<1||d.copies()>99)bad();
    // 场景编码是外部系统的稳定入口；行锁保护检查与保存，不能仅靠页面只读防止改码。
    if(id!=null){
      var existing=statements.lockScene(c.tenantId(),uuid(id));
      if(existing.isEmpty())throw new PrintRejection("PRINT_SCENE_CONFLICT",409);
      if(!d.code().equals(existing.getFirst().get("code")))throw new PrintRejection("PRINT_SCENE_CODE_IMMUTABLE",409);
    }
    contract(c,d.contract());
    var templates=statements.sceneTemplate(c.tenantId(),uuid(d.templateId()));
    if(templates.isEmpty()||!d.contract().equals(templates.getFirst().get("data_contract")))throw new PrintRejection("PRINT_SCENE_TEMPLATE_INVALID",409);
    if(id==null)statements.createScene(c.tenantId(),UUID.randomUUID().toString(),d.code(),d.name().trim(),d.contract(),d.templateId(),d.copies(),d.enabled(),c.actorId());
    else if(statements.updateScene(d.code(),d.name().trim(),d.contract(),uuid(d.templateId()),d.copies(),d.enabled(),c.actorId(),c.tenantId(),uuid(id),d.revision())==0)throw new PrintRejection("PRINT_SCENE_CONFLICT",409);
    return scenes(c);
  });}

  /** 返回当前操作者在当前租户保存的单据；不通过打印权限扩大其他业务单据读取范围。 */
  public List<Map<String,Object>> documents(PrintContext c){allow(c,"print.job:submit");var rows=statements.ownDocuments(c.tenantId(),c.actorId());for(var row:rows)row.put("data_json",content.decode(row.get("data_json").toString()));return rows;}

  /** 按契约白名单保存真实录入单据；不从客户端接受租户、账号或任意数据源 URL。 */
  
  public List<Map<String,Object>> saveDocument(PrintContext c,DocumentRequest d){return transaction.required(()->{
    allow(c,"print.job:submit");
    if(d==null||d.number()==null||d.number().isBlank()||d.number().length()>128)bad();
    var scene=scene(c,d.sceneId());
    PrintDataPolicy.documentNumber(content.plain(d.data()),d.number().trim());
    validateData(d.data(),contract(c,scene.get("data_contract").toString()));
    statements.createDocument(c.tenantId(),UUID.randomUUID().toString(),d.sceneId(),d.number().trim(),content.snapshot(d.data()),c.actorId());
    return documents(c);
  });}

  /**
   * 仅凭服务端单据 ID 创建任务；固定契约、发布模板和数据快照，浏览器不能替换打印字段。
   * @param c 当前人员上下文及幂等键
   * @param id 本人手工单据 UUID
   * @param d 用户选定设备、PDF/PRINTER 渠道及 1—99 份数
   * @return 已持久化的任务，网络失败后同一幂等键可重放
   */
  
  public Map<String,Object> print(PrintContext c,String id,DocumentPrintRequest d){return transaction.required(()->{
    allow(c,"print.job:submit");
    var docs=statements.ownDocument(c.tenantId(),uuid(id),c.actorId());
    if(docs.isEmpty())throw new PrintRejection("PRINT_BUSINESS_OBJECT_NOT_FOUND",404);
    var doc=docs.getFirst();var scene=scene(c,doc.get("scene_id").toString());
    var template=statements.publishedTemplate(c.tenantId(),(String)scene.get("template_id"));
    if(template.isEmpty()||!scene.get("data_contract").equals(template.getFirst().get("data_contract")))throw new PrintRejection("PRINT_SCENE_TEMPLATE_INVALID",409);
    var data=content.decode(doc.get("data_json").toString());
    // 两种 HTTP 入口都须看到同一有效契约；历史模板未冻结架构也不能回退为无约束打印。
    validateData(data,contract(c,scene.get("data_contract").toString()));
    return runtime.submit(c,new com.appnexus.printing.api.PrintData.JobDraft(
        "CONFIGURED_DOCUMENT",id,scene.get("template_id").toString(),d.outputType(),d.deviceId(),d.copies(),data));
  });}

  /** 包内外部编排入口：先由 PrintOutputService 验证专用权限，再按场景校验契约；不开放任意模板调用。 */
  public Map<String,Object> submitSnapshot(PrintContext c,String sceneId,String sourceId,Object payload,String output,String device,int copies){
    Object data=content.plain(payload);
    var scene=scene(c,sceneId);if(sourceId==null||sourceId.isBlank()||sourceId.length()>128)bad();
    validateData(data,contract(c,scene.get("data_contract").toString()));
    var versions=statements.snapshotTemplate(c.tenantId(),(String)scene.get("template_id"));
    if(versions.isEmpty()||!scene.get("data_contract").equals(versions.getFirst().get("data_contract")))throw new PrintRejection("PRINT_SCENE_TEMPLATE_INVALID",409);
    return runtime.submit(c,new com.appnexus.printing.api.PrintData.JobDraft("EXTERNAL_DOCUMENT",sourceId,scene.get("template_id").toString(),output,device,copies,data));
  }

  /** 契约目录可供设计者和录入人员查看；仅提供字段元数据与合成样例。 */
  public List<Map<String,Object>> contracts(PrintContext c){
    allow(c,CONTRACT_READ_PERMISSIONS.toArray(String[]::new));
    var values=new ArrayList<Map<String,Object>>(PrintDesignCatalog.contracts());
    values.addAll(businessContracts);
    for(String definition:statements.contractDefinitions(c.tenantId())){
      @SuppressWarnings("unchecked") var node=(Map<String,Object>)content.decode(definition);Map<String,Object> value=new LinkedHashMap<>();
      value.put("code",node.get("code"));value.put("nameZh",node.get("nameZh"));value.put("nameEn",node.get("nameEn"));value.put("designOnly",false);value.put("fields",node.get("fields"));values.add(value);
    }
    return values;
  }

  /**
   * 配置租户字段契约；只增不改，防止已有发布模板因字段重命名失效。
   * @param c 当前设计人员可信上下文
   * @param definitionValue 名称、带版本编码和最多 50 个文本/明细字段；不接受脚本、URL 数据源或 SQL
   * @return 新契约目录；编码冲突由控制器返回 409
   */
  
  public List<Map<String,Object>> createContract(PrintContext c,Object definitionValue){return transaction.required(()->{
    Object normalized=content.plain(definitionValue);allow(c,"print.template:design");
    var reserved=new HashSet<Object>();PrintDesignCatalog.contracts().forEach(value->reserved.add(value.get("code")));
    var clean=PrintDataPolicy.cleanContract(normalized,reserved);
    // 领域清洗已验证 code 为字符串，存储端口继续使用语义明确的字符串契约。
    statements.createContract(c.tenantId(),(String)clean.get("code"),content.object(clean),c.actorId());return contracts(c);
  });}

  /** 字段键限制为直接标识符，拒绝路径表达式与原型键；明细不允许再嵌套明细。 */


  /** 客户端上报队列仅更新自身站点和实例范围；最大 100 个队列，名称不能包含控制字符。 */
  
  public void report(PrintContext c,QueueReport d){transaction.run(()->{
    allow(c,"print.agent:operate");
    if(d==null||!PrintRules.validCode(d.site())||d.agent()==null||d.agent().isBlank()||d.agent().length()>128||d.queues()==null||d.queues().size()>100)bad();
    for(String name:d.queues())if(name==null||name.isBlank()||name.length()>255||name.chars().anyMatch(Character::isISOControl))bad();
    platform.authorizeAgentQueues(c,d.site(),d.agent());
    statements.deleteAgentQueues(c.tenantId(),d.site(),d.agent(),c.actorId());
    for(String name:new LinkedHashSet<>(d.queues()))statements.reportAgentQueue(c.tenantId(),d.site(),d.agent(),name,c.actorId());
  });}

  /** 只列出最近 120 秒认证上报的客户端队列，登记设备仍需独立设备管理权限。 */
  public List<Map<String,Object>> queues(PrintContext c){allow(c,"print.device:manage");return statements.queues(c.tenantId());}

  private Map<String,Object> scene(PrintContext c,String id){var rows=statements.enabledScene(c.tenantId(),uuid(id));if(rows.isEmpty())throw new PrintRejection("PRINT_SCENE_UNAVAILABLE",409);return rows.getFirst();}
  private Map<String,Object> contract(PrintContext c,String code){return contracts(c).stream().filter(value->value.get("code").equals(code)).findFirst().orElseThrow(()->new PrintRejection("PRINT_CONTRACT_INVALID",400));}

  /** 仅接受契约内文本和明细，最多 500 行、单元格 2000 字符；不执行表达式或任意字段路径。 */
  @SuppressWarnings("unchecked")
  private void validateData(Object data,Map<String,Object> definition){PrintDataPolicy.validate(content.plain(data),definition);}
  private void allow(PrintContext c,String... permissions){if(Arrays.stream(permissions).noneMatch(p->platform.allowed(c,p)))throw new PrintRejection("PRINT_ACCESS_DENIED",403);}
  private static String uuid(String value){try{return UUID.fromString(value).toString();}catch(Exception e){throw new PrintRejection("PRINT_IDENTIFIER_INVALID",400);}}
  private static void bad(){throw new PrintRejection("PRINT_INPUT_INVALID",400);}
  
  
  
  
 /** 保留缺省null语义；对象/数组/标量仍由原固定模板或数据契约校验，不将缺省转成NullNode。 */

}
