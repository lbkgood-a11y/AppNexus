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
  static JdbcTemplate jdbc;
  static DriverManagerDataSource source;
  CodingManagement service;
  boolean allowed=true;
  String tenant=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString();
  static final Rule RULE=new Rule(List.of(new Segment("TEXT","PU",0),new Segment("DATE","yyyyMMdd",0),new Segment("SEQUENCE","",6)),"DAY","Asia/Shanghai");
  @BeforeAll static void database(){source=new DriverManagerDataSource(MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",MYSQL.getUsername(),MYSQL.getPassword());jdbc=new JdbcTemplate(source);var populator=new ResourceDatabasePopulator(new ClassPathResource("db/migration/V123__business_object_coding.sql"));populator.setSqlScriptEncoding("UTF-8");populator.execute(source);}
  @BeforeEach void setup(){
    var declaration=new CodingDeclaration().descriptor();
    var descriptor=new Descriptor("TestDocument","测试单据","Test document","test",1,Category.TRANSACTION,List.of(),List.of(new Operation("create","新增","Create","test:create","TestDocument.create",null,List.of(),new InteractionContract("TestDocument.create",1,"TestDocument","create","新增","Create",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,"TestDocument.create","",List.of(),Set.of(),"test.document.create",1))))),null,List.of(new CodingField("code","create",64)));
    var objects=org.mockito.Mockito.mock(BusinessObjectManagement.class);
    service=com.appnexus.platform.persistence.businessobject.BusinessObjectPersistence.coding(jdbc,new DataSourceTransactionManager(source),new BusinessObjectPlatform(){
      public boolean platformAdministrator(String a){return false;}
      public boolean tenantAdministrator(Context c){return allowed;}
      public boolean active(Context c){return allowed;}
      public boolean tenantExists(String t){return true;}
      public boolean codingAllowed(Context c,String operation,String permission){return allowed&&operation.equals("TestDocument.create")&&permission.equals("test:create");}
    },List.of(()->descriptor),objects);
    service.save(context(),"TestDocument","purchase",new com.appnexus.platform.businessobject.CodingManagement.Input(-1,"code","采购编码",true,RULE));
  }
  private Context context(){return new Context(tenant,actor,UUID.randomUUID().toString());}
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
    service.save(other,"TestDocument","purchase",new com.appnexus.platform.businessobject.CodingManagement.Input(-1,"code","其他租户",true,RULE));assertThat(service.issue(other,"TestDocument","purchase")).isEqualTo(code);
    assertThatThrownBy(()->service.save(context(),"TestDocument","purchase",new com.appnexus.platform.businessobject.CodingManagement.Input(0,"code","变更",true,new Rule(RULE.segments(),"NEVER","UTC")))).hasMessage("CODING_RULE_FROZEN");
    var before=service.configurations(context(),"TestDocument").getFirst();assertThat(before.get("createdBy").toString()).isEqualToIgnoringCase(actor);assertThat(before.get("createdAt")).isNotNull();
    assertThatThrownBy(()->service.save(context(),"TestDocument","purchase",new com.appnexus.platform.businessobject.CodingManagement.Input(7,"code","覆盖",true,RULE))).hasMessage("CODING_VERSION_CONFLICT");assertThat(service.configurations(context(),"TestDocument").getFirst()).isEqualTo(before);
    service.save(context(),"TestDocument","purchase",new com.appnexus.platform.businessobject.CodingManagement.Input(0,"code","停用",false,RULE));assertThatThrownBy(()->service.issue(request,"TestDocument","purchase")).hasMessage("CODING_DISABLED");
    allowed=false;assertThatThrownBy(()->service.options(context(),"TestDocument","code")).hasMessage("CODING_ACCESS_DENIED");assertThatThrownBy(()->service.configurations(context(),"TestDocument")).hasMessage("CODING_ACCESS_DENIED");
  }
  @Test void reusedRequestForDifferentEncoderIsRejected(){
    service.save(context(),"TestDocument","other",new com.appnexus.platform.businessobject.CodingManagement.Input(-1,"code","另一编码器",true,new Rule(List.of(new Segment("TEXT","OTHER",0),new Segment("SEQUENCE","",6)),"NEVER","UTC")));
    var request=context();service.issue(request,"TestDocument","purchase");assertThatThrownBy(()->service.issue(request,"TestDocument","other")).hasMessage("CODING_IDEMPOTENCY_CONFLICT");
  }
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
    service.save(context(),"TestDocument","other",new com.appnexus.platform.businessobject.CodingManagement.Input(-1,"code","相同规则",true,RULE));
    service.issue(context(),"TestDocument","purchase");
    assertThatThrownBy(()->service.issue(context(),"TestDocument","other")).hasMessage("CODING_CODE_OR_REQUEST_CONFLICT");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_counter WHERE tenant_id=UUID_TO_BIN(?) AND encoder_key='other'",Integer.class,tenant)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?) AND encoder_key='other'",Integer.class,tenant)).isZero();
  }
}
