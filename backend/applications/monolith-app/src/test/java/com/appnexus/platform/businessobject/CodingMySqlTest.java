package com.appnexus.platform.businessobject;

import com.appnexus.platform.appapi.businessobject.CodingDeclaration;

import com.appnexus.platform.businessobject.BusinessObjectManagement;

import com.appnexus.platform.businessobject.CodingManagement;

import static org.assertj.core.api.Assertions.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.BusinessCoding.*;
import com.appnexus.contracts.businessobject.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

/** 真实 MySQL 验证跨进程锁语义、幂等、租户隔离、规则冻结和失败审计回滚，不用内存数据库模拟锁。 */
@Testcontainers
class CodingMySqlTest {
  @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
  static final String LEGACY_TENANT=UUID.randomUUID().toString(),LEGACY_ACTOR=UUID.randomUUID().toString();
  static JdbcTemplate jdbc;
  static DriverManagerDataSource source;
  CodingManagement service;
  boolean allowed=true,ruleAllowed=true;
  String tenant=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString();
  static final Rule RULE=new Rule(List.of(new Segment("TEXT","PU",0),new Segment("DATE","yyyyMMdd",0),new Segment("SEQUENCE","",6)),"DAY","Asia/Shanghai");
  @BeforeAll static void database(){source=new DriverManagerDataSource(MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",MYSQL.getUsername(),MYSQL.getPassword());jdbc=new JdbcTemplate(source);var populator=new ResourceDatabasePopulator(new ClassPathResource("db/migration/V123__business_object_coding.sql"));populator.setSqlScriptEncoding("UTF-8");populator.execute(source);
    jdbc.update("INSERT INTO business_object_encoder(tenant_id,object_type,encoder_key,field_key,name,rule_json,created_by,updated_by) VALUES(UUID_TO_BIN(?),'TestDocument','legacy','code','历史编码',?,UUID_TO_BIN(?),UUID_TO_BIN(?))",LEGACY_TENANT,"{\"segments\":[{\"kind\":\"TEXT\",\"value\":\"OLD\",\"width\":0},{\"kind\":\"SEQUENCE\",\"value\":\"\",\"width\":6}],\"reset\":\"NEVER\",\"zoneId\":\"UTC\"}",LEGACY_ACTOR,LEGACY_ACTOR);
    jdbc.update("INSERT INTO business_object_code_counter(tenant_id,object_type,encoder_key,period_key,sequence_value) VALUES(UUID_TO_BIN(?),'TestDocument','legacy','ALL',41)",LEGACY_TENANT);
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V126__independent_coding_rules.sql")).execute(source);}
  @BeforeEach void setup(){
    var declaration=new CodingDeclaration().descriptor();
    var descriptor=new Descriptor("TestDocument","测试单据","Test document","test",1,Category.TRANSACTION,List.of(),List.of(new Operation("create","新增","Create","test:create","TestDocument.create",null,List.of(),new InteractionContract("TestDocument.create",1,"TestDocument","create","新增","Create",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,"TestDocument.create","",List.of(),Set.of(),"test.document.create",1))))),null,List.of(new CodingField("code","create",64)));
    var objects=org.mockito.Mockito.mock(BusinessObjectManagement.class);
    service=com.appnexus.platform.persistence.businessobject.BusinessObjectPersistence.coding(jdbc,new DataSourceTransactionManager(source),new BusinessObjectPlatform(){
      public boolean platformAdministrator(String a){return false;}
      public boolean tenantAdministrator(Context c){return allowed;}
      public boolean active(Context c){return allowed;}
      public boolean tenantExists(String t){return true;}
      public boolean operationAllowed(Context c,String operation,String permission){return allowed&&((ruleAllowed&&operation.startsWith("CodingRule.")&&permission.equals("coding.rule:manage"))||(operation.equals("TestDocument.create")&&permission.equals("test:create")));}
    },List.of(()->descriptor),objects);
    service.save(context(),"TestDocument","purchase",input(-1,"code","采购编码",true,RULE));
  }
  /** 旧行为回归的准备工具：先经公开用例创建独立规则，再构造绑定输入。 */
  private CodingManagement.Input input(long revision,String field,String name,boolean enabled,Rule rule){
    String key="R"+UUID.randomUUID().toString().replace("-","");
    service.saveRule(context(),key,new CodingManagement.RuleInput(-1,name,rule));
    return new CodingManagement.Input(revision,field,name,enabled,key,1);
  }
  private CodingManagement.Input currentInput(long revision,String name,boolean enabled){
    var row=service.configurations(context(),"TestDocument").getFirst();
    return new CodingManagement.Input(revision,"code",name,enabled,(String)row.get("ruleKey"),((Number)row.get("ruleVersion")).longValue());
  }
  private Context context(){return new Context(tenant,actor,UUID.randomUUID().toString());}
  /** V126真实迁移后历史绑定保持规则语义和创建账号，下一次发号延续原计数。 */
  @Test void legacyMigrationKeepsCounterAndAudit(){
    var c=new Context(LEGACY_TENANT,LEGACY_ACTOR,UUID.randomUUID().toString());
    var row=service.configurations(c,"TestDocument").getFirst();
    assertThat(row.get("ruleKey").toString()).startsWith("R");assertThat(((Number)row.get("ruleVersion")).longValue()).isEqualTo(1);
    assertThat(row.get("createdBy").toString()).isEqualToIgnoringCase(LEGACY_ACTOR);
    assertThat(service.rules(c).getFirst().get("createdBy").toString()).isEqualToIgnoringCase(LEGACY_ACTOR);
    assertThat(service.issue(c,"TestDocument","legacy")).isEqualTo("OLD000042");
    assertThat(service.issue(c,"TestDocument","legacy")).isEqualTo("OLD000042");
  }
  @Test void exampleAndRuleBoundaries(){
    assertThat(com.appnexus.platform.businessobject.internal.domain.coding.CodingRules.render(RULE,java.time.LocalDate.of(2026,11,9),1)).isEqualTo("PU20261109000001");
    assertThatThrownBy(()->com.appnexus.platform.businessobject.internal.domain.coding.CodingRules.validate(new Rule(List.of(new Segment("SEQUENCE","",6)),"DAY","UTC"),64)).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->com.appnexus.platform.businessobject.internal.domain.coding.CodingRules.render(RULE,java.time.LocalDate.now(),1000000)).hasMessage("CODING_SEQUENCE_EXHAUSTED");
    assertThatThrownBy(()->com.appnexus.platform.businessobject.internal.domain.coding.CodingRules.validate(RULE,10)).hasMessage("CODING_RULE_INVALID");
  }
  @Test void parallelIssueAndIdempotency() throws Exception {
    var pool=Executors.newFixedThreadPool(8);
    try{
      var tasks=new ArrayList<Callable<String>>();for(int i=0;i<24;i++)tasks.add(()->service.issue(context(),"TestDocument","purchase"));
      var results=pool.invokeAll(tasks);var unique=new HashSet<String>();for(var result:results)unique.add(result.get());assertThat(unique).hasSize(24);
      var request=context();String code=service.issue(request,"TestDocument","purchase");assertThat(service.issue(request,"TestDocument","purchase")).isEqualTo(code);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant)).isEqualTo(25);
    }finally{pool.shutdownNow();}
  }
  @Test void tenantIsolationFrozenRuleRevocationAndAudit(){
    var request=context();String code=service.issue(request,"TestDocument","purchase");
    var other=new Context(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString());
    assertThat(service.configurations(other,"TestDocument")).isEmpty();assertThatThrownBy(()->service.issue(other,"TestDocument","purchase")).hasMessage("CODING_UNAVAILABLE");
    service.saveRule(other,"otherTenant",new CodingManagement.RuleInput(-1,"其他租户",RULE));service.save(other,"TestDocument","purchase",new CodingManagement.Input(-1,"code","其他租户",true,"otherTenant",1));assertThat(service.issue(other,"TestDocument","purchase")).isEqualTo(code);
    assertThatThrownBy(()->service.save(context(),"TestDocument","purchase",input(0,"code","变更",true,new Rule(RULE.segments(),"NEVER","UTC")))).hasMessage("CODING_RULE_FROZEN");
    var before=service.configurations(context(),"TestDocument").getFirst();assertThat(before.get("createdBy").toString()).isEqualToIgnoringCase(actor);assertThat(before.get("createdAt")).isNotNull();
    assertThatThrownBy(()->service.save(context(),"TestDocument","purchase",input(7,"code","覆盖",true,RULE))).hasMessage("CODING_VERSION_CONFLICT");assertThat(service.configurations(context(),"TestDocument").getFirst()).isEqualTo(before);
    service.save(context(),"TestDocument","purchase",currentInput(0,"停用",false));assertThatThrownBy(()->service.issue(request,"TestDocument","purchase")).hasMessage("CODING_DISABLED");
    allowed=false;assertThatThrownBy(()->service.options(context(),"TestDocument","code")).hasMessage("CODING_ACCESS_DENIED");assertThatThrownBy(()->service.configurations(context(),"TestDocument")).hasMessage("CODING_ACCESS_DENIED");
  }
  @Test void reusedRequestForDifferentEncoderIsRejected(){
    service.save(context(),"TestDocument","other",input(-1,"code","另一编码器",true,new Rule(List.of(new Segment("TEXT","OTHER",0),new Segment("SEQUENCE","",6)),"NEVER","UTC")));
    var request=context();service.issue(request,"TestDocument","purchase");assertThatThrownBy(()->service.issue(request,"TestDocument","other")).hasMessage("CODING_IDEMPOTENCY_CONFLICT");
  }
  /** 固定版本不自动升级，过期版本发布与悬空/跨租户引用失败且不污染绑定审计。 */
  @Test void independentVersionsAndBindingAuthorization(){
    service.saveRule(context(),"shared",new CodingManagement.RuleInput(-1,"共享规则",RULE));
    service.save(context(),"TestDocument","shared",new CodingManagement.Input(-1,"code","绑定",true,"shared",1));
    service.saveRule(context(),"shared",new CodingManagement.RuleInput(1,"新版",new Rule(List.of(new Segment("TEXT","NEW",0),new Segment("SEQUENCE","",6)),"NEVER","UTC")));
    assertThat(service.rules(context()).stream().filter(r->r.get("ruleKey").equals("shared")).count()).isEqualTo(2);
    assertThat(service.issue(context(),"TestDocument","shared")).startsWith("PU");
    assertThatThrownBy(()->service.saveRule(context(),"shared",new CodingManagement.RuleInput(1,"过期",RULE))).hasMessage("CODING_VERSION_CONFLICT");
    var before=service.configurations(context(),"TestDocument");
    assertThatThrownBy(()->service.save(context(),"TestDocument","missing",new CodingManagement.Input(-1,"code","无效",true,"shared",99))).hasMessage("CODING_RULE_UNAVAILABLE");
    assertThat(service.configurations(context(),"TestDocument")).isEqualTo(before);
    var other=new Context(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString());
    assertThat(service.rules(other)).isEmpty();
    assertThatThrownBy(()->service.save(other,"TestDocument","x",new CodingManagement.Input(-1,"code","越租户",true,"shared",1))).hasMessage("CODING_RULE_UNAVAILABLE");
    ruleAllowed=false;
    assertThatThrownBy(()->service.rules(context())).hasMessage("CODING_ACCESS_DENIED");
    assertThatThrownBy(()->service.saveRule(context(),"denied",new CodingManagement.RuleInput(-1,"拒绝",RULE))).hasMessage("CODING_ACCESS_DENIED");
    assertThat(service.ruleOptions(context(),"TestDocument")).isNotEmpty();
    assertThat(service.options(context(),"TestDocument","code")).isNotEmpty();
  }
  /** 同一最新版本并发追加只允许一个成功，不产生重复版本或部分提交。 */
  @Test void concurrentVersionAppendHasOneWinner() throws Exception {
    service.saveRule(context(),"versionRace",new CodingManagement.RuleInput(-1,"初版",RULE));
    var pool=Executors.newFixedThreadPool(2);
    try{
      var tasks=List.<Callable<Boolean>>of(()->appendVersion(),()->appendVersion());
      int success=0;for(var result:pool.invokeAll(tasks))if(result.get())success++;
      assertThat(success).isEqualTo(1);
      assertThat(service.rules(context()).stream().filter(r->r.get("ruleKey").equals("versionRace")).count()).isEqualTo(2);
    }finally{pool.shutdownNow();}
  }
  private boolean appendVersion(){try{service.saveRule(context(),"versionRace",new CodingManagement.RuleInput(1,"并发版本",RULE));return true;}catch(Rejection e){assertThat(e.getMessage()).isEqualTo("CODING_VERSION_CONFLICT");return false;}}
  /** 同键并发只允许一条回执；撤权后即使有历史结果也必须拒绝重放。 */
  @Test void concurrentReplayDoesNotConsumeMoreNumbersAndRevocationStillDenies() throws Exception {
    var request=context();var pool=Executors.newFixedThreadPool(6);
    try{
      var tasks=new ArrayList<Callable<String>>();for(int n=0;n<12;n++)tasks.add(()->service.issue(request,"TestDocument","purchase"));
      var unique=new HashSet<String>();for(var result:pool.invokeAll(tasks))unique.add(result.get());assertThat(unique).hasSize(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT sequence_value FROM business_object_code_counter WHERE tenant_id=UUID_TO_BIN(?)",Long.class,tenant)).isEqualTo(1);
      allowed=false;assertThatThrownBy(()->service.issue(request,"TestDocument","purchase")).hasMessage("CODING_ACCESS_DENIED");
    }finally{pool.shutdownNow();}
  }
  /** 两编码器输出碰撞时，唯一约束必须回滚失败编码器的流水初始化及审计。 */
  @Test void codeCollisionRollsBackCounterAndReceipt(){
    service.save(context(),"TestDocument","other",input(-1,"code","相同规则",true,RULE));
    service.issue(context(),"TestDocument","purchase");
    assertThatThrownBy(()->service.issue(context(),"TestDocument","other")).hasMessage("CODING_CODE_OR_REQUEST_CONFLICT");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_counter WHERE tenant_id=UUID_TO_BIN(?) AND encoder_key='other'",Integer.class,tenant)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?) AND encoder_key='other'",Integer.class,tenant)).isZero();
  }
}
