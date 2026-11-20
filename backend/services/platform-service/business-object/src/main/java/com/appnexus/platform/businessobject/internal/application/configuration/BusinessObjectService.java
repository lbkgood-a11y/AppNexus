package com.appnexus.platform.businessobject.internal.application.configuration;

import com.appnexus.platform.businessobject.port.*;
import com.appnexus.contracts.businessobject.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.workflow.*;
import static com.appnexus.platform.businessobject.internal.domain.configuration.ConfigurationRules.*;
import java.util.*;
import java.util.function.Supplier;

/** 业务对象配置与绑定解析；不读取业务表、不执行业务动作，所有租户语句显式限定可信租户。 */
public final class BusinessObjectService implements com.appnexus.platform.businessobject.BusinessObjectManagement {
  /** 从唯一业务声明生成交互目录；无消息执行器时不生成可绑定的消息能力。 */
  @Override public List<InteractionContract> contracts(){return types.values().stream().flatMap(d->d.operations().stream()).map(Operation::interaction).sorted(Comparator.comparing(InteractionContract::code)).toList();}
  private Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing=()->null;
  /** 延迟装配公开打印目录，关闭打印不应影响纯直接行为。 */
  public void printing(Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing){this.printing=printing;}
  /** 返回行为投影契约兼容场景，配置管理授权与打印数据读取权分离。 */
  public List<com.appnexus.contracts.printing.BusinessPrinting.Scene> scenes(Context c,String type,String code){
    manage(c,type);var a=action(type,code);if(a.printing()==null)return List.of();var port=printing.get();if(port==null)return List.of();
    return port.scenes(new com.appnexus.contracts.printing.PrintContext(c.tenantId(),c.actorId(),c.requestId()),a.printing().dataContract());
  }
  private final ConfigurationStore store;
  private final ConfigurationTransaction transaction;
  private final BusinessObjectPlatform platform;
  private final Supplier<WorkflowDefinitionCatalog> workflows;
  private final Map<String,Descriptor> types;
  private final ConfigurationCodec codec;
  /** 检查实际发布依赖，不创建解析或业务任务，配置管理者不能借检查读取业务对象原文。 */
  public List<Check> readiness(Context c,String type){
    manage(c,type);var d=descriptor(type);var result=new ArrayList<Check>();
    boolean enabled=Boolean.TRUE.equals(store.typeEnabled(type));
    var rows=store.publishedConfiguration(c.tenantId(),type);
    var bindings=rows.isEmpty()?List.<Binding>of():connected(type,bindings(rows.getFirst().get("published")));
    for(var a:d.operations()){
      result.add(new Check(a.operationCode(),"STATIC","TYPE_ENABLED",enabled,type));
      result.add(new Check(a.operationCode(),"STATIC","PAGE_RELATION",relationships(type).stream().anyMatch(r->r.operationCode().equals(a.operationCode())),a.operationId()));
      var selected=bindings.stream().filter(b->b.operationCode().equals(a.operationCode())).toList();
      result.add(new Check(a.operationCode(),"STATIC","PUBLISHED_BINDING",!selected.isEmpty(),type));
      for(var b:selected){
        if(b.service().equals("WORKFLOW"))result.add(new Check(a.operationCode(),"STATIC","WORKFLOW_VERSION",options(c,type,a.operationCode()).stream().anyMatch(o->o.id().equals(b.definitionId())&&o.version()==b.definitionVersion()),b.definitionId()+":"+b.definitionVersion()));
        if(b.service().equals("PRINT")){result.add(new Check(a.operationCode(),"STATIC","PRINT_SCENE",scenes(c,type,a.operationCode()).stream().anyMatch(s->s.code().equals(b.sceneCode())),b.sceneCode()));
          var port=printing.get();if(port!=null)result.add(new Check(a.operationCode(),"LIVE","RENDERER_ONLINE",port.rendererOnline(new com.appnexus.contracts.printing.PrintContext(c.tenantId(),c.actorId(),c.requestId())),b.sceneCode()));}
      }
    }return List.copyOf(result);
  }

  /**
   * 装配纯应用用例；重复声明阻止启动，延迟目录避免装配循环。
   * @param store 固定对象配置存储端口，与事务共用数据源
   * @param transaction 整个配置用例事务；嵌套调用必须加入现有事务
   * @param codec 版本化配置快照编解码端口，不暴露 JSON 框架
   * @param platform 可信平台授权端口，每次执行重新复核
   * @param workflows 延迟工作流目录，关闭时允许返回 null
   * @param providers 唯一业务对象声明集合，重复及冲突启动即拒绝
   */
  public BusinessObjectService(ConfigurationStore store, ConfigurationTransaction transaction, ConfigurationCodec codec, BusinessObjectPlatform platform,
      Supplier<WorkflowDefinitionCatalog> workflows, List<Provider> providers) {
    this.store=store; this.transaction=transaction; this.codec=codec; this.platform=platform; this.workflows=workflows;
    var map=new TreeMap<String,Descriptor>();
    var contractCodes=new HashSet<String>();
    var dataSchemas=new HashMap<String,com.appnexus.contracts.data.DataContract>();
    for(var p:providers) {
      var d=p.descriptor();
      if(d.objectType()==null||!d.objectType().matches("[A-Za-z][A-Za-z0-9_]{0,63}")||d.version()<1||map.put(d.objectType(),d)!=null)
        throw new IllegalArgumentException("BUSINESS_OBJECT_DESCRIPTOR_INVALID");
      var keys=new HashSet<String>();
      for(var f:d.fields()) if(!keys.add(f.key())||!Set.of("TEXT","NUMBER","BOOLEAN").contains(f.type()))
        throw new IllegalArgumentException("BUSINESS_OBJECT_FIELD_INVALID");
      // 专用API操作允许空公共能力；保存绑定和执行仍必须命中实际声明的服务，不能默认补BUSINESS。
      var actions=new HashSet<String>();
      for(var a:d.operations()) if(a.operationCode()==null||!a.operationCode().matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}")||!actions.add(a.operationCode())||a.permissionCode()==null||a.permissionCode().isBlank()
          ||!a.operationId().equals(d.objectType()+"."+a.operationCode())
          ||!Set.of("BUSINESS","WORKFLOW","PRINT").containsAll(a.supportedServices()))throw new IllegalArgumentException("BUSINESS_OBJECT_ACTION_INVALID");
      // 同身份数据契约不可由不同业务提供者悄悄覆盖；部署即拒绝冲突声明。
      for(var action:d.operations())for(var capability:action.interaction().capabilities()){
        var schema=capability.schema();var prior=dataSchemas.putIfAbsent(schema.code()+":"+schema.version(),schema);
        if(prior!=null&&!prior.equals(schema))throw new IllegalArgumentException("DATA_CONTRACT_SCHEMA_CONFLICT");
      }
      var operations=new HashSet<String>();
      for(var a:d.operations()){
        if(!a.interaction().objectType().equals(d.objectType())||!contractCodes.add(a.interaction().code()))throw new IllegalArgumentException("INTERACTION_IDENTITY_INVALID");
        if(!operations.add(a.operationId()))throw new IllegalArgumentException("BUSINESS_OBJECT_PAGE_OPERATION_INVALID");
        for(var alias:a.aliases())if(alias.operationId()==null||alias.operationId().isBlank()||alias.pageOperation()==null||!operations.add(alias.operationId()))throw new IllegalArgumentException("BUSINESS_OBJECT_PAGE_OPERATION_INVALID");
        if(a.supportedServices().contains("PRINT")&&(a.printing()==null||a.printing().dataContract()==null||a.printing().dataContract().isBlank()))throw new IllegalArgumentException("BUSINESS_OBJECT_PRINT_CONTRACT_REQUIRED");
      }
      for(var a:d.operations())if(a.supportedServices().contains("WORKFLOW")){
        var contract=approval(d,a);
        if(contract.purpose()==null||!contract.purpose().matches("[A-Za-z][A-Za-z0-9_]{0,63}")||contract.entry()==null||contract.entry().isBlank()||contract.outcomes().isEmpty())throw new IllegalArgumentException("BUSINESS_OBJECT_APPROVAL_CONTRACT_REQUIRED");
      }
    }
    types=Map.copyOf(map);

  }

  /** 管理目录；租户可见全部已授权类型并明确展示启停状态，避免授权成功后被运行开关静默隐藏。 */
  public List<Map<String,Object>> catalog(Context c, boolean global) {
    if(global) requirePlatform(c); else requireManager(c);
    return types.values().stream().sorted(Comparator.comparing(Descriptor::objectType)).filter(d->global||granted(c.tenantId(),d.objectType()))
      .map(d->{var state=store.typeState(d.objectType());
        return Map.<String,Object>of("descriptor",global?d:effectiveDescriptor(d),"enabled",!state.isEmpty()&&Boolean.TRUE.equals(state.getFirst().get("enabled")),
          "revision",state.isEmpty()?0L:state.getFirst().get("revision"));}).toList();
  }

  /** 已接入操作与管控状态；revision=-1 为无覆盖记录，代码登记自动生效。 */
  public Map<String,Object> pageRelationships(Context c,String type) {
    requirePlatform(c);descriptor(type);
    var rows=store.pageRelationshipState(type);
    return Map.of("revision",rows.isEmpty()?-1L:rows.getFirst().get("revision"),"relationships",relationships(type),"candidates",pageCandidates(type));
  }

  /**
   * 内部迁移及管控辅助：将有效入口集合转换为禁用覆盖；HTTP 不开放关系编辑。
   * @param revision 首次 -1，后续原样回传读取版本；空集合表示显式关闭所有页面入口
   * @param list 当前完整关系集，不是增量；最多 100 项，不改变业务接口和权限
   */
  public void savePageRelationships(Context c,String type,long revision,List<PageRelationship> list) {
    transaction.run(()->{requirePlatform(c);descriptor(type);
      if(list==null||list.size()>100)fail(400,"BUSINESS_OBJECT_PAGE_RELATION_INVALID");
      var candidates=pageCandidates(type);var used=new HashSet<String>();
      for(var relation:list){if(relation==null||!used.add(relation.operationId())||candidates.stream().noneMatch(candidate->candidate.operationId().equals(relation.operationId())&&candidate.compatibleOperationCode().equals(relation.operationCode())))fail(400,"BUSINESS_OBJECT_PAGE_RELATION_INVALID");}
      var disabled=candidates.stream().filter(candidate->list.stream().noneMatch(r->r.operationId().equals(candidate.operationId()))).map(PageCandidate::operationId).toList();
      if(revision==-1){if(store.createPageRelationships(type,write(list),write(disabled),c.actorId())!=1)fail(409,"BUSINESS_OBJECT_VERSION_CONFLICT");}
      else if(store.updatePageRelationships(write(list),write(disabled),c.actorId(),type,revision)!=1)fail(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
      audit(c,type,"PAGE_RELATION_SAVED");
    });
  }

  /**
   * 独立入口启停控制；关系本身不可编辑，不影响历史结果回调。
   * @param revision 读取的控制版本，首次为 -1；并发更新拒绝覆盖
   * @param operation 代码登记的操作编码，不接受任意页面或接口
   * @param enabled 是否允许新命令；启用仍受租户、权限和业务状态约束
   * @param reason 必填管控原因，最多 200 字；不得包含业务原文或敏感数据
   */
  public void controlPage(Context c,String type,long revision,String operation,boolean enabled,String reason){
    requirePlatform(c);
    if(reason==null||reason.isBlank()||reason.length()>200)fail(400,"BUSINESS_OBJECT_CONTROL_REASON_REQUIRED");
    var candidate=pageCandidates(type).stream().filter(p->p.operationId().equals(operation)).findFirst().orElseThrow(()->new Rejection(400,"BUSINESS_OBJECT_PAGE_OPERATION_INVALID"));
    transaction.run(()->{
      var list=new ArrayList<>(relationships(type));list.removeIf(r->r.operationId().equals(operation));
      if(enabled)list.add(new PageRelationship(candidate.compatibleOperationCode(),operation));
      savePageRelationships(c,type,revision,list);
      store.appendEntryControl(type,operation,enabled,reason.trim(),c.actorId());
    });
  }

  /** 页面业务命令执行前实时复核；撤掉平台关系后不能用旧按钮或旧配置绕过。 */
  @Override public void verifyPageOperation(Context c,String type,String code,String operation) {
    requireActive(c);action(type,code);
    if(relationships(type).stream().noneMatch(r->r.operationCode().equals(code)&&r.operationId().equals(operation)))fail(403,"BUSINESS_OBJECT_PAGE_RELATION_DISABLED");
  }

  private List<PageCandidate> pageCandidates(String type){
    var result=new ArrayList<PageCandidate>();
    for(var a:descriptor(type).operations()){
      if(a.pageOperation()!=null)result.add(new PageCandidate(a.operationId(),a.operationCode(),a.permissionCode(),a.pageOperation()));
      for(var alias:a.aliases())result.add(new PageCandidate(alias.operationId(),a.operationCode(),a.permissionCode(),alias.pageOperation()));
    }
    return List.copyOf(result);
  }
  /** 代码登记自动建立关系，平台仅应用禁用覆盖；未保存控制记录时所有真实入口可用。 */
  private List<PageRelationship> relationships(String type){
    var rows=store.disabledOperations(type);
    List<String> disabled=rows.isEmpty()?List.of():codec.operations(rows.getFirst().get("disabled_operations"));
    return pageCandidates(type).stream().filter(c->!disabled.contains(c.operationId())).map(c->new PageRelationship(c.compatibleOperationCode(),c.operationId())).toList();
  }

  /** 租户目录投影有效入口；行为仍采用统一身份，禁用只影响对应入口。 */
  private Descriptor effectiveDescriptor(Descriptor d){
    var relations=relationships(d.objectType());
    var actions=d.operations().stream().map(a->new Operation(a.operationCode(),a.nameZh(),a.nameEn(),a.permissionCode(),a.operationId(),
      relations.stream().anyMatch(r->r.operationCode().equals(a.operationCode())&&r.operationId().equals(a.operationId()))?a.pageOperation():null,
      a.aliases().stream().filter(alias->relations.stream().anyMatch(r->r.operationCode().equals(a.operationCode())&&r.operationId().equals(alias.operationId()))).toList(),a.interaction())).toList();
    return new Descriptor(d.objectType(),d.nameZh(),d.nameEn(),d.module(),d.version(),d.category(),d.fields(),actions,d.dataAccess(),d.codingFields());
  }

  /** 平台激活使用乐观锁；不自动向租户授权。 */
  public void activate(Context c,String type,long revision,boolean enabled) {
    transaction.run(()->{requirePlatform(c); descriptor(type);
      store.ensureType(type);
      if(store.updateTypeState(enabled,type,revision)!=1) fail(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
      audit(c,type,"TYPE_STATE");});
  }

  /** 返回已配置授权，不读取平台租户明细；租户名称由平台现有目录提供。 */
  public List<Map<String,Object>> grants(Context c,String type) {
    requirePlatform(c); descriptor(type);
    return store.tenantGrants(type);
  }

  /** 平台按类型授权；收回维护资格保留已发布绑定，不清除历史实例。 */
  public void grant(Context c,String type,String tenant,boolean enabled) {
    UUID.fromString(tenant);
    transaction.run(()->{requirePlatform(c); descriptor(type);
      if(!platform.tenantExists(tenant))fail(404,"BUSINESS_OBJECT_TENANT_UNAVAILABLE");
      store.saveTenantGrant(tenant,type,enabled);
      audit(new Context(tenant,c.actorId(),c.requestId()),type,"TENANT_GRANT");});
  }

  /** 当前草稿及发布信息；首次尚未保存时 revision=-1。 */
  public Map<String,Object> configuration(Context c,String type) {
    manage(c,type);
    var rows=store.configuration(c.tenantId(),type);
    if(rows.isEmpty()) return Map.of("revision",-1,"publishedVersion",0,"bindings",List.of());
    var r=rows.getFirst(); return Map.of("revision",r.get("revision"),"publishedVersion",r.get("published_version"),"bindings",connected(type,bindings(r.get("draft"))));
  }

  /** 按行为读取兼容审批目录；仅 BUSINESS 行为不要求工作流可用。 */
  public List<WorkflowDefinitionCatalog.Option> options(Context c,String type,String operationCode) {
    manage(c,type); var d=descriptor(type);var action=action(type,operationCode);
    if(!action.supportedServices().contains("WORKFLOW"))return List.of();
    var contract=approval(d,action);var port=workflows.get();
    if(port==null) return List.of();
    return port.published(wc(c),type,contract.purpose(),contract.entry(),contract.outcomes());
  }

  /** 保存只更新草稿；revision=-1 仅首次，绑定上限 50、每条条件最多 10 项，防止无界配置。 */
  public void save(Context c,String type,long revision,List<Binding> list) {
    transaction.run(()->{manage(c,type); var normalized=connected(type,list); validate(c,type,normalized);
      if(revision==-1) {
        if(store.createDraft(c.tenantId(),type,write(normalized))!=1) fail(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
      } else if(store.updateDraft(write(normalized),c.tenantId(),type,revision)!=1) fail(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
      audit(c,type,"DRAFT_SAVED");});
  }

  /** 原子发布并保留历史；运行中实例不读取最新映射。 */
  public void publish(Context c,String type,long revision) {
    transaction.run(()->{manage(c,type);
      var rows=store.lockConfiguration(c.tenantId(),type);
      if(rows.isEmpty()||((Number)rows.getFirst().get("revision")).longValue()!=revision) fail(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
      var r=rows.getFirst();var list=connected(type,bindings(r.get("draft")));validate(c,type,list);
      int version=((Number)r.get("published_version")).intValue()+1;
      store.appendConfigurationVersion(c.tenantId(),type,version,descriptor(type).version(),write(list),c.actorId());
      store.publishConfiguration(write(list),write(list),version,c.tenantId(),type);
      audit(c,type,"PUBLISHED");});
  }

  /** 每种已登记业务行为独立选择策略；共享权限码不能代替行为隔离。 */
  @Override public Resolution resolveOperation(Context c,String type,String operationCode,String object,long basis,Map<String,String> facts) {
    action(type,operationCode);
    UUID.fromString(c.requestId());UUID.fromString(object);if(basis<0) fail(400,"BUSINESS_OBJECT_BASIS_INVALID");
    requireActive(c);checkFacts(descriptor(type),facts);
    return transaction.call(()->{
      // 已解析请求也不能绕过紧急停用；完成业务的重放由业务业务对象状态直接返回。
      enabled(type);
      // 配置行共享锁固定发布版本；唯一键处理不同进程同请求的并发，冲突后重读原结果。
      var prior=store.resolutionByRequest(c.tenantId(),c.actorId(),c.requestId());
      if(!prior.isEmpty()) {var saved=check(c,prior.getFirst(),type,object,basis,facts);if(!saved.operationCode().equals(operationCode))fail(409,"BUSINESS_OBJECT_IDEMPOTENCY_CONFLICT");return saved;}
      var rows=store.sharePublishedConfiguration(c.tenantId(),type);
      if(rows.isEmpty()) fail(409,"BUSINESS_OBJECT_FLOW_NOT_BOUND");
      var r=rows.getFirst();
      Integer descriptorVersion=store.descriptorVersion(c.tenantId(),type,r.get("published_version"));
      if(descriptorVersion==null||descriptorVersion!=descriptor(type).version())fail(409,"BUSINESS_OBJECT_CONTRACT_CHANGED");
      var matches=matchingBindings(type,operationCode,r.get("published"),facts);
      if(matches.size()!=1) fail(409,matches.isEmpty()?"BUSINESS_OBJECT_FLOW_NOT_MATCHED":"BUSINESS_OBJECT_FLOW_MATCH_CONFLICT");
      var b=matches.getFirst();
      store.createResolution(UUID.randomUUID().toString(),c.tenantId(),c.actorId(),c.requestId(),type,object,basis,r.get("published_version"),b.definitionId(),b.service().equals("WORKFLOW")?b.definitionVersion():null,write(new TreeMap<>(facts)),b.operationCode(),b.service(),b.sceneCode(),b.service().equals("PRINT")?action(type,operationCode).printing().dataContract():null);
      return check(c,store.requiredResolutionByRequest(c.tenantId(),c.actorId(),c.requestId()),type,object,basis,facts);
    });
  }

  /** 只读预检复用同一绑定过滤规则，不写入业务对象解析及幂等记录。 */
  @Override public String configurationStatus(Context c,String type,String code,Map<String,String> facts){
    requireActive(c);action(type,code);checkFacts(descriptor(type),facts);
    if(!Boolean.TRUE.equals(store.typeEnabled(type)))return "TYPE_DISABLED";
    if(!granted(c.tenantId(),type))return "TENANT_NOT_GRANTED";
    var rows=store.publishedConfigurationState(c.tenantId(),type);
    if(rows.isEmpty())return "CONFIGURATION_NOT_PUBLISHED";
    var row=rows.getFirst();var version=store.descriptorVersion(c.tenantId(),type,row.get("published_version"));
    if(version==null||version!=descriptor(type).version())return "CONTRACT_CHANGED";
    var matches=matchingBindings(type,code,row.get("published"),facts);
    if(matches.size()!=1)return matches.isEmpty()?"CONFIGURATION_NOT_MATCHED":"CONFIGURATION_AMBIGUOUS";
    var binding=matches.getFirst();
    if(binding.service().equals("WORKFLOW")){
      var port=workflows.get();if(port==null)return "WORKFLOW_UNAVAILABLE";
      var contract=approval(descriptor(type),action(type,code));
      if(!port.available(wc(c),type,contract.purpose(),contract.entry(),contract.outcomes(),binding.definitionId(),binding.definitionVersion()))return "WORKFLOW_TARGET_INVALID";
    }
    if(binding.service().equals("PRINT")){
      var port=printing.get();if(port==null)return "PRINT_UNAVAILABLE";
      var contract=action(type,code).printing();
      if(contract==null||!port.sceneAvailable(new com.appnexus.contracts.printing.PrintContext(c.tenantId(),c.actorId(),c.requestId()),contract.dataContract(),binding.sceneCode()))return "PRINT_TARGET_INVALID";
    }
    return "READY";
  }

  /**
   * 正式解析与只读预检共享唯一匹配规则，防止权限/状态允许但配置判定漂移。
   * @param type 已登记对象编码
   * @param code 对象内操作编码
   * @param published 当前读取的发布快照；正式执行由调用事务固定版本
   * @param facts 业务服务授权读取的非敏感条件事实
   * @return 全部匹配绑定；调用者必须拒绝零匹配及多匹配，不取第一条兜底
   */
  private List<Binding> matchingBindings(String type,String code,Object published,Map<String,String> facts){
    var operation=action(type,code);
    return connected(type,bindings(published)).stream()
      .filter(binding->binding.operationCode().equals(code)&&operation.supportedServices().contains(binding.service()))
      .filter(binding->binding.conditions().stream().allMatch(condition->test(descriptor(type),condition,facts))).toList();
  }

  @Override public Resolution verify(Context c,String id,String type,String object,long basis,Map<String,String> facts) {
    requireActive(c); enabled(type);
    var rows=store.resolutionById(c.tenantId(),c.actorId(),id);
    if(rows.isEmpty()) {fail(403,"BUSINESS_OBJECT_RESOLUTION_DENIED");}
    var result=check(c,rows.getFirst(),type,object,basis,facts);
    if(!result.service().equals("WORKFLOW"))fail(409,"BUSINESS_OBJECT_APPROVAL_REQUIRED");
    return result;
  }

  private Resolution check(Context c,Map<String,Object> r,String type,String object,long basis,Map<String,String> facts) {
    Map<String,String> saved=codec.facts(r.get("facts"));
    if(!type.equals(r.get("object_type"))||!object.equals(r.get("object_id"))||basis!=((Number)r.get("basis_version")).longValue()||!saved.equals(facts)) fail(409,"BUSINESS_OBJECT_IDEMPOTENCY_CONFLICT");
    return new Resolution(r.get("id").toString(),c.tenantId(),type,object,basis,((Number)r.get("configuration_version")).intValue(),Objects.toString(r.get("definition_id"),null),r.get("definition_version")==null?0:((Number)r.get("definition_version")).intValue(),saved,r.get("operation_code").toString(),r.get("service").toString(),Objects.toString(r.get("scene_code"),null),Objects.toString(r.get("data_contract"),null));
  }
  @Override public Resolution verifyApproval(Context c,String id,String type,String object,long basis,Map<String,String> facts,String purpose,String entry) {
    var result=verify(c,id,type,object,basis,facts);var contract=approval(descriptor(type),action(type,result.operationCode()));
    if(!contract.purpose().equals(purpose)||!contract.entry().equals(entry))fail(409,"BUSINESS_OBJECT_APPROVAL_CONTRACT_REQUIRED");
    return result;
  }

  private void validate(Context c,String type,List<Binding> list) {
    if(list==null||list.isEmpty()||list.size()>50) fail(400,"BUSINESS_OBJECT_BINDINGS_INVALID");
    var optionCache=new HashMap<String,List<WorkflowDefinitionCatalog.Option>>();var ids=new HashSet<String>();var signatures=new HashSet<String>();
    for(var b:list) {
      UUID.fromString(b.id());
      if(!ids.add(b.id())||b.name()==null||b.name().isBlank()||b.name().length()>100||b.conditions().size()>10) fail(400,"BUSINESS_OBJECT_BINDINGS_INVALID");
      var action=descriptor(type).operations().stream().filter(a->a.operationCode().equals(b.operationCode())).findFirst().orElseThrow(()->new Rejection(400,"BUSINESS_OBJECT_ACTION_INVALID"));
      // 策略属于行为，不属于按钮；至少一个入口可用即可维护，具体入口执行时独立复核。
      if(pageCandidates(type).stream().anyMatch(p->p.compatibleOperationCode().equals(b.operationCode()))&&relationships(type).stream().noneMatch(r->r.operationCode().equals(b.operationCode())))fail(409,"BUSINESS_OBJECT_PAGE_RELATION_DISABLED");
      if(action.interaction().trigger()!=InteractionContract.Trigger.USER_ACTION)fail(400,"INTERACTION_TRIGGER_UNSUPPORTED");
      if(!action.supportedServices().contains(b.service()))fail(400,"BUSINESS_OBJECT_ACTION_INVALID");
      if(b.service().equals("PRINT")) {if(b.definitionId()!=null||b.definitionVersion()!=0||scenes(c,type,b.operationCode()).stream().noneMatch(scene->scene.code().equals(b.sceneCode())))fail(409,"BUSINESS_OBJECT_PRINT_SCENE_INVALID");}
      else if(b.service().equals("BUSINESS")) {if(b.definitionId()!=null||b.definitionVersion()!=0)fail(400,"BUSINESS_OBJECT_DIRECT_FLOW_INVALID");}
      else if(optionCache.computeIfAbsent(b.operationCode(),key->options(c,type,key)).stream().noneMatch(o->o.id().equals(b.definitionId())&&o.version()==b.definitionVersion())) fail(409,"BUSINESS_OBJECT_FLOW_INCOMPATIBLE");
      if(!b.service().equals("PRINT")&&b.sceneCode()!=null)fail(400,"BUSINESS_OBJECT_PRINT_SCENE_INVALID");
      if(!signatures.add(b.operationCode()+":"+write(b.conditions()))) fail(409,"BUSINESS_OBJECT_FLOW_MATCH_CONFLICT");
      for(var condition:b.conditions()) {
        var field=descriptor(type).fields().stream().filter(f->f.key().equals(condition.field())).findFirst().orElseThrow(()->new Rejection(400,"BUSINESS_OBJECT_FIELD_INVALID"));
        if(condition.value()==null||condition.value().length()>200||!Set.of("EQ","NE","GT","GE","LT","LE").contains(condition.operator())||(!field.type().equals("NUMBER")&&!Set.of("EQ","NE").contains(condition.operator()))) fail(400,"BUSINESS_OBJECT_CONDITION_INVALID");
        scalar(field,condition.value());
      }
    }
    for(var b:list)if(b.conditions().isEmpty()&&list.stream().filter(other->other.operationCode().equals(b.operationCode())).count()>1) fail(409,"BUSINESS_OBJECT_FLOW_MATCH_CONFLICT");
  }
  /** 核验显式操作属于当前行为；不补默认值、不接受跨类型或空选择。 */
  private List<Binding> connected(String type,List<Binding> list) {
    if(list==null)fail(400,"BUSINESS_OBJECT_BINDINGS_INVALID");
    return list.stream().map(b->{
      var action=descriptor(type).operations().stream().filter(a->a.operationCode().equals(b.operationCode())).findFirst().orElseThrow(()->new Rejection(400,"BUSINESS_OBJECT_ACTION_INVALID"));
      String operation=Objects.requireNonNull(b.operationId(),"INTERACTION_OPERATION_REQUIRED");
      if(!action.operationId().equals(operation)&&action.aliases().stream().noneMatch(a->a.operationId().equals(operation)))fail(400,"BUSINESS_OBJECT_PAGE_OPERATION_INVALID");
      return new Binding(b.id(),b.name(),b.definitionId(),b.definitionVersion(),b.conditions(),b.operationCode(),b.service(),action.operationId(),b.sceneCode());
    }).toList();
  }
  private Descriptor descriptor(String type) {var d=types.get(type);if(d==null)fail(404,"BUSINESS_OBJECT_TYPE_UNAVAILABLE");return d;}
  private Operation action(String type,String operationCode){return descriptor(type).operations().stream().filter(a->a.operationCode().equals(operationCode)).findFirst().orElseThrow(()->new Rejection(400,"BUSINESS_OBJECT_ACTION_INVALID"));}
  /** 审批视图只能从当前行为的统一交互声明生成，不从业务对象或其他行为推断。 */
  private ApprovalContract approval(Descriptor d,Operation a){return Objects.requireNonNull(a.approval(),"INTERACTION_WORKFLOW_CONTRACT_REQUIRED");}
  private void enabled(String type) {if(!Boolean.TRUE.equals(store.typeEnabled(type))) fail(409,"BUSINESS_OBJECT_TYPE_DISABLED");}
  /** 配置资格仅由租户授权决定；类型启停在运行解析/启动时独立检查，允许预先维护未启用类型。 */
  private boolean granted(String tenant,String type) {return Boolean.TRUE.equals(store.tenantGranted(tenant,type));}
  private void manage(Context c,String type) {requireManager(c);descriptor(type);if(!granted(c.tenantId(),type)) fail(403,"BUSINESS_OBJECT_CONFIGURATION_DENIED");}
  private void requireManager(Context c) {if(!platform.tenantAdministrator(c))fail(403,"BUSINESS_OBJECT_CONFIGURATION_DENIED");}
  private void requirePlatform(Context c) {if(!platform.platformAdministrator(c.actorId()))fail(403,"BUSINESS_OBJECT_PLATFORM_DENIED");}
  private void requireActive(Context c) {if(!platform.active(c))fail(403,"BUSINESS_OBJECT_ACCESS_DENIED");}
  private WorkflowContext wc(Context c) {return new WorkflowContext(c.tenantId(),c.actorId(),c.requestId());}
  private void audit(Context c,String type,String action) {store.appendAudit(c.tenantId(),c.actorId(),type,action);}
  private List<Binding> bindings(Object value) {return codec.bindings(value);}
  private String write(Object value) {return codec.encode(value);}
  private static void fail(int status,String code) {throw new Rejection(status,code);}
}
