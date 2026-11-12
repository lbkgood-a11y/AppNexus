package com.appnexus.document;

import com.appnexus.contracts.document.*;
import static com.appnexus.contracts.document.Documents.*;
import com.appnexus.contracts.workflow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 单据配置与绑定解析；不读取业务表、不执行业务动作，所有租户语句显式限定可信租户。 */
public final class DocumentService implements DocumentBindings {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final DocumentPlatform platform;
  private final Supplier<WorkflowDefinitionCatalog> workflows;
  private final Map<String,Descriptor> types;
  private final ObjectMapper json = new ObjectMapper();

  /** 装配代码目录；重复类型或不支持的字段类型阻止启动，工作流目录延迟取得避免装配环。 */
  public DocumentService(JdbcTemplate jdbc, PlatformTransactionManager manager, DocumentPlatform platform,
      Supplier<WorkflowDefinitionCatalog> workflows, List<Provider> providers) {
    this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.platform=platform; this.workflows=workflows;
    var map=new TreeMap<String,Descriptor>();
    for(var p:providers) {
      var d=p.descriptor();
      if(d.typeCode()==null||!d.typeCode().matches("[A-Za-z][A-Za-z0-9_]{0,63}")||d.version()<1||map.put(d.typeCode(),d)!=null)
        throw new IllegalArgumentException("DOCUMENT_DESCRIPTOR_INVALID");
      var keys=new HashSet<String>();
      for(var f:d.fields()) if(!keys.add(f.key())||!Set.of("TEXT","NUMBER","BOOLEAN").contains(f.type()))
        throw new IllegalArgumentException("DOCUMENT_FIELD_INVALID");
      var actions=new HashSet<String>();
      for(var a:d.actions()) if(!actions.add(a.actionCode())||a.permissionCode()==null||a.permissionCode().isBlank()
          ||!a.operationKey().equals(d.typeCode()+"."+a.actionCode())||a.supportedModes().isEmpty()
          ||!Set.of("DIRECT","APPROVAL").containsAll(a.supportedModes()))throw new IllegalArgumentException("DOCUMENT_ACTION_INVALID");
    }
    types=Map.copyOf(map);
  }

  /** 管理目录；租户可见全部已授权类型并明确展示启停状态，避免授权成功后被运行开关静默隐藏。 */
  public List<Map<String,Object>> catalog(Context c, boolean global) {
    if(global) requirePlatform(c); else requireManager(c);
    return types.values().stream().sorted(Comparator.comparing(Descriptor::typeCode)).filter(d->global||granted(c.tenantId(),d.typeCode()))
      .map(d->{var state=jdbc.queryForList("SELECT enabled,revision FROM doc_type WHERE type_code=?",d.typeCode());
        return Map.<String,Object>of("descriptor",d,"enabled",!state.isEmpty()&&Boolean.TRUE.equals(state.getFirst().get("enabled")),
          "revision",state.isEmpty()?0L:state.getFirst().get("revision"));}).toList();
  }

  /** 平台激活使用乐观锁；不自动向租户授权。 */
  public void activate(Context c,String type,long revision,boolean enabled) {
    tx.executeWithoutResult(s->{requirePlatform(c); descriptor(type);
      jdbc.update("INSERT IGNORE INTO doc_type(type_code) VALUES(?)",type);
      if(jdbc.update("UPDATE doc_type SET enabled=?,revision=revision+1 WHERE type_code=? AND revision=?",enabled,type,revision)!=1) fail(409,"DOCUMENT_VERSION_CONFLICT");
      audit(c,type,"TYPE_STATE");});
  }

  /** 返回已配置授权，不读取平台租户明细；租户名称由平台现有目录提供。 */
  public List<Map<String,Object>> grants(Context c,String type) {
    requirePlatform(c); descriptor(type);
    return jdbc.queryForList("SELECT tenant_id,enabled FROM doc_tenant_grant WHERE type_code=? ORDER BY tenant_id",type);
  }

  /** 平台按类型授权；收回维护资格保留已发布绑定，不清除历史实例。 */
  public void grant(Context c,String type,String tenant,boolean enabled) {
    UUID.fromString(tenant);
    tx.executeWithoutResult(s->{requirePlatform(c); descriptor(type);
      if(!platform.tenantExists(tenant))fail(404,"DOCUMENT_TENANT_UNAVAILABLE");
      jdbc.update("INSERT INTO doc_tenant_grant(tenant_id,type_code,enabled) VALUES(?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)",tenant,type,enabled);
      audit(new Context(tenant,c.actorId(),c.requestId()),type,"TENANT_GRANT");});
  }

  /** 当前草稿及发布信息；首次尚未保存时 revision=-1。 */
  public Map<String,Object> configuration(Context c,String type) {
    manage(c,type);
    var rows=jdbc.queryForList("SELECT revision,published_version,draft FROM doc_configuration WHERE tenant_id=? AND type_code=?",c.tenantId(),type);
    if(rows.isEmpty()) return Map.of("revision",-1,"publishedVersion",0,"bindings",List.of());
    var r=rows.getFirst(); return Map.of("revision",r.get("revision"),"publishedVersion",r.get("published_version"),"bindings",bindings(r.get("draft")));
  }

  /** 兼容的具体发布版本；查看单据配置无需拥有 workflow:start。 */
  public List<WorkflowDefinitionCatalog.Option> options(Context c,String type) {
    manage(c,type); var d=descriptor(type); var port=workflows.get();
    if(port==null) return List.of();
    return port.published(wc(c),type,d.purpose(),d.entry(),d.outcomes());
  }

  /** 保存只更新草稿；revision=-1 仅首次，绑定上限 50、每条条件最多 10 项，防止无界配置。 */
  public void save(Context c,String type,long revision,List<Binding> list) {
    tx.executeWithoutResult(s->{manage(c,type); validate(c,type,list);
      if(revision==-1) {
        if(jdbc.update("INSERT IGNORE INTO doc_configuration(tenant_id,type_code,draft) VALUES(?,?,?)",c.tenantId(),type,write(list))!=1) fail(409,"DOCUMENT_VERSION_CONFLICT");
      } else if(jdbc.update("UPDATE doc_configuration SET draft=?,revision=revision+1 WHERE tenant_id=? AND type_code=? AND revision=?",write(list),c.tenantId(),type,revision)!=1) fail(409,"DOCUMENT_VERSION_CONFLICT");
      audit(c,type,"DRAFT_SAVED");});
  }

  /** 原子发布并保留历史；运行中实例不读取最新映射。 */
  public void publish(Context c,String type,long revision) {
    tx.executeWithoutResult(s->{manage(c,type);
      var rows=jdbc.queryForList("SELECT * FROM doc_configuration WHERE tenant_id=? AND type_code=? FOR UPDATE",c.tenantId(),type);
      if(rows.isEmpty()||((Number)rows.getFirst().get("revision")).longValue()!=revision) fail(409,"DOCUMENT_VERSION_CONFLICT");
      var r=rows.getFirst();var list=bindings(r.get("draft"));validate(c,type,list);
      int version=((Number)r.get("published_version")).intValue()+1;
      jdbc.update("INSERT INTO doc_configuration_version(tenant_id,type_code,version,descriptor_version,bindings,actor) VALUES(?,?,?,?,?,?)",c.tenantId(),type,version,descriptor(type).version(),write(list),c.actorId());
      jdbc.update("UPDATE doc_configuration SET published=draft,published_version=?,revision=revision+1 WHERE tenant_id=? AND type_code=?",version,c.tenantId(),type);
      audit(c,type,"PUBLISHED");});
  }

  @Override public Resolution resolve(Context c,String type,String object,long basis,Map<String,String> facts) {
    UUID.fromString(c.requestId());UUID.fromString(object);if(basis<0) fail(400,"DOCUMENT_BASIS_INVALID");
    requireActive(c);checkFacts(descriptor(type),facts);
    return tx.execute(s->{
      // 已解析请求也不能绕过紧急停用；完成业务的重放由业务单据状态直接返回。
      enabled(type);
      // 配置行共享锁固定发布版本；唯一键处理不同进程同请求的并发，冲突后重读原结果。
      var prior=jdbc.queryForList("SELECT * FROM doc_resolution WHERE tenant_id=? AND actor=? AND request_key=?",c.tenantId(),c.actorId(),c.requestId());
      if(!prior.isEmpty()) return check(c,prior.getFirst(),type,object,basis,facts);
      var rows=jdbc.queryForList("SELECT published,published_version FROM doc_configuration WHERE tenant_id=? AND type_code=? AND published_version>0 FOR SHARE",c.tenantId(),type);
      if(rows.isEmpty()) fail(409,"DOCUMENT_FLOW_NOT_BOUND");
      var r=rows.getFirst();
      Integer descriptorVersion=jdbc.queryForObject("SELECT descriptor_version FROM doc_configuration_version WHERE tenant_id=? AND type_code=? AND version=?",Integer.class,c.tenantId(),type,r.get("published_version"));
      if(descriptorVersion==null||descriptorVersion!=descriptor(type).version())fail(409,"DOCUMENT_CONTRACT_CHANGED");
      var matches=bindings(r.get("published")).stream().filter(b->b.conditions().stream().allMatch(x->test(descriptor(type),x,facts))).toList();
      if(matches.size()!=1) fail(409,matches.isEmpty()?"DOCUMENT_FLOW_NOT_MATCHED":"DOCUMENT_FLOW_MATCH_CONFLICT");
      var b=matches.getFirst();
      jdbc.update("INSERT IGNORE INTO doc_resolution(id,tenant_id,actor,request_key,type_code,object_id,basis_version,configuration_version,definition_id,definition_version,facts,action_code,submission_mode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),c.tenantId(),c.actorId(),c.requestId(),type,object,basis,r.get("published_version"),b.definitionId(),b.mode().equals("DIRECT")?null:b.definitionVersion(),write(new TreeMap<>(facts)),b.actionCode(),b.mode());
      return check(c,jdbc.queryForMap("SELECT * FROM doc_resolution WHERE tenant_id=? AND actor=? AND request_key=?",c.tenantId(),c.actorId(),c.requestId()),type,object,basis,facts);
    });
  }

  @Override public Resolution verify(Context c,String id,String type,String object,long basis,Map<String,String> facts) {
    requireActive(c); enabled(type);
    var rows=jdbc.queryForList("SELECT * FROM doc_resolution WHERE tenant_id=? AND actor=? AND id=?",c.tenantId(),c.actorId(),id);
    if(rows.isEmpty()) {fail(403,"DOCUMENT_RESOLUTION_DENIED");}
    var result=check(c,rows.getFirst(),type,object,basis,facts);
    if(!result.mode().equals("APPROVAL"))fail(409,"DOCUMENT_APPROVAL_REQUIRED");
    return result;
  }

  private Resolution check(Context c,Map<String,Object> r,String type,String object,long basis,Map<String,String> facts) {
    Map<String,String> saved=read(r.get("facts"),new TypeReference<>(){});
    if(!type.equals(r.get("type_code"))||!object.equals(r.get("object_id"))||basis!=((Number)r.get("basis_version")).longValue()||!saved.equals(facts)) fail(409,"DOCUMENT_IDEMPOTENCY_CONFLICT");
    return new Resolution(r.get("id").toString(),c.tenantId(),type,object,basis,((Number)r.get("configuration_version")).intValue(),Objects.toString(r.get("definition_id"),null),r.get("definition_version")==null?0:((Number)r.get("definition_version")).intValue(),saved,r.get("action_code").toString(),r.get("submission_mode").toString());
  }

  private void validate(Context c,String type,List<Binding> list) {
    if(list==null||list.isEmpty()||list.size()>50) fail(400,"DOCUMENT_BINDINGS_INVALID");
    var options=list.stream().anyMatch(b->b.mode().equals("APPROVAL"))?options(c,type):List.<WorkflowDefinitionCatalog.Option>of();var ids=new HashSet<String>();var signatures=new HashSet<String>();
    for(var b:list) {
      UUID.fromString(b.id());
      if(!ids.add(b.id())||b.name()==null||b.name().isBlank()||b.name().length()>100||b.conditions().size()>10) fail(400,"DOCUMENT_BINDINGS_INVALID");
      var action=descriptor(type).actions().stream().filter(a->a.actionCode().equals(b.actionCode())).findFirst().orElseThrow(()->new Rejection(400,"DOCUMENT_ACTION_INVALID"));
      if(!b.actionCode().equals("submit")||!action.supportedModes().contains(b.mode()))fail(400,"DOCUMENT_ACTION_INVALID");
      if(b.mode().equals("DIRECT")) {if(b.definitionId()!=null||b.definitionVersion()!=0)fail(400,"DOCUMENT_DIRECT_FLOW_INVALID");}
      else if(options.stream().noneMatch(o->o.id().equals(b.definitionId())&&o.version()==b.definitionVersion())) fail(409,"DOCUMENT_FLOW_INCOMPATIBLE");
      if(!signatures.add(write(b.conditions()))) fail(409,"DOCUMENT_FLOW_MATCH_CONFLICT");
      for(var condition:b.conditions()) {
        var field=descriptor(type).fields().stream().filter(f->f.key().equals(condition.field())).findFirst().orElseThrow(()->new Rejection(400,"DOCUMENT_FIELD_INVALID"));
        if(condition.value()==null||condition.value().length()>200||!Set.of("EQ","NE","GT","GE","LT","LE").contains(condition.operator())||(!field.type().equals("NUMBER")&&!Set.of("EQ","NE").contains(condition.operator()))) fail(400,"DOCUMENT_CONDITION_INVALID");
        scalar(field,condition.value());
      }
    }
    if(list.size()>1&&list.stream().anyMatch(b->b.conditions().isEmpty())) fail(409,"DOCUMENT_FLOW_MATCH_CONFLICT");
  }
  private void checkFacts(Descriptor d,Map<String,String> facts) {
    if(facts==null||facts.size()!=d.fields().size()) fail(400,"DOCUMENT_FACTS_INVALID");
    for(var f:d.fields()) {String value=facts.get(f.key());if(value==null||value.length()>200) fail(400,"DOCUMENT_FACTS_INVALID");scalar(f,value);}
  }
  private void scalar(Field f,String value) {
    if(f.type().equals("NUMBER")) {try {new BigDecimal(value);} catch(NumberFormatException e) {fail(400,"DOCUMENT_NUMBER_INVALID");}}
    if(f.type().equals("BOOLEAN")&&!Set.of("true","false").contains(value)) fail(400,"DOCUMENT_BOOLEAN_INVALID");
  }
  private boolean test(Descriptor d,Condition c,Map<String,String> facts) {
    String v=facts.get(c.field());if(v==null)return false;
    var field=d.fields().stream().filter(f->f.key().equals(c.field())).findFirst().orElseThrow();
    int compare=field.type().equals("NUMBER")?new BigDecimal(v).compareTo(new BigDecimal(c.value())):v.compareTo(c.value());
    return switch(c.operator()){case "EQ"->compare==0;case "NE"->compare!=0;case "GT"->compare>0;case "GE"->compare>=0;case "LT"->compare<0;case "LE"->compare<=0;default->false;};
  }
  private Descriptor descriptor(String type) {var d=types.get(type);if(d==null)fail(404,"DOCUMENT_TYPE_UNAVAILABLE");return d;}
  private void enabled(String type) {if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT COALESCE(MAX(enabled),FALSE) FROM doc_type WHERE type_code=?",Boolean.class,type))) fail(409,"DOCUMENT_TYPE_DISABLED");}
  /** 配置资格仅由租户授权决定；类型启停在运行解析/启动时独立检查，允许预先维护未启用类型。 */
  private boolean granted(String tenant,String type) {return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doc_tenant_grant WHERE tenant_id=? AND type_code=? AND enabled=TRUE)",Boolean.class,tenant,type));}
  private void manage(Context c,String type) {requireManager(c);descriptor(type);if(!granted(c.tenantId(),type)) fail(403,"DOCUMENT_CONFIGURATION_DENIED");}
  private void requireManager(Context c) {if(!platform.tenantAdministrator(c))fail(403,"DOCUMENT_CONFIGURATION_DENIED");}
  private void requirePlatform(Context c) {if(!platform.platformAdministrator(c.actorId()))fail(403,"DOCUMENT_PLATFORM_DENIED");}
  private void requireActive(Context c) {if(!platform.active(c))fail(403,"DOCUMENT_ACCESS_DENIED");}
  private WorkflowContext wc(Context c) {return new WorkflowContext(c.tenantId(),c.actorId(),c.requestId());}
  private void audit(Context c,String type,String action) {jdbc.update("INSERT INTO doc_audit(tenant_id,actor,type_code,action) VALUES(?,?,?,?)",c.tenantId(),c.actorId(),type,action);}
  private List<Binding> bindings(Object value) {return read(value,new TypeReference<>(){});}
  private <T> T read(Object value,TypeReference<T> type) {try{return json.readValue(value.toString(),type);}catch(Exception e){throw new IllegalStateException("DOCUMENT_STORED_CONTRACT_INVALID",e);}}
  private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("DOCUMENT_INPUT_INVALID",e);}}
  private static void fail(int status,String code) {throw new Rejection(status,code);}
}
