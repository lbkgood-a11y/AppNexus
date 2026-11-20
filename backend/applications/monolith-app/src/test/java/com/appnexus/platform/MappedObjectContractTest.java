package com.appnexus.platform;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 第三个对象仅声明字段/状态映射，复用生产工厂；不实现运行器或能力处理器。 */
@SpringBootTest
@ActiveProfiles("local")
class MappedObjectContractTest extends DatabaseTestSupport {
  @Autowired ObjectRecordRuntimeFactory factory;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Test void thirdObjectNeedsOnlyContractAndMapping(){
    // 隔离验证表显式包含完整审计，不向生产迁移增加演示业务。
    jdbc.execute("CREATE TABLE test_contract_record(tenant_id VARCHAR(36),id VARCHAR(36),organization_id VARCHAR(36),applicant VARCHAR(36),version BIGINT,state VARCHAR(20),instance_id VARCHAR(64),delivery_id VARCHAR(128),label VARCHAR(100),created_by BINARY(16),created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),updated_by BINARY(16),updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(tenant_id,id))");
    try{
      var operation=new Operation("submit","提交","Submit","test.contract:submit","ThirdContract.submit",null,List.of(),new InteractionContract("ThirdContract.submit",1,"ThirdContract","submit","提交","Submit",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,"ThirdContract.submit","",List.of(new Field("label","名称","Label","TEXT","",true)),Set.of(),"third.data",1))));
      Provider provider=()->new Descriptor("ThirdContract","第三对象","Third object","test",1,Category.TRANSACTION,List.of(),List.of(operation));
      var text=Encoding.TEXT;
      var mapping=new ObjectRecordMapping("test_contract_record",new Column("tenant_id",text),new Column("id",text),new Column("organization_id",text),new Column("applicant",text),"version","state","instance_id","delivery_id",null,Map.of("label",new Value(new Column("label",text),Transform.VALUE,0)),Map.of("submit",new Transition(Set.of("DRAFT"),"ACCEPTED",false)),null,true);
      var runtime=factory.create(provider,mapping,(c,id,op,service)->{});
      String tenant=UUID.randomUUID().toString(),id=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString(),organization=UUID.randomUUID().toString();
      jdbc.update("INSERT INTO test_contract_record(tenant_id,id,organization_id,applicant,version,state,label) VALUES(?,?,?,?,0,'DRAFT','真实第三对象')",tenant,id,organization,actor);
      var context=new Context(tenant,actor,UUID.randomUUID().toString());var tx=new TransactionTemplate(manager);
      assertThat(tx.execute(status->runtime.read(context,"ThirdContract",id,"submit",0,Set.of("label"))).values().get("label")).isEqualTo("真实第三对象");
      assertThatThrownBy(()->tx.execute(status->runtime.read(new Context(UUID.randomUUID().toString(),actor,UUID.randomUUID().toString()),"ThirdContract",id,"submit",0,Set.of()))).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->tx.execute(status->runtime.read(context,"ThirdContract",id,"submit",1,Set.of()))).hasMessage("BUSINESS_OBJECT_VERSION_CONFLICT");
      tx.execute(status->runtime.execute(context,"ThirdContract",id,"submit",0,Map.of()));
      assertThat(jdbc.queryForObject("SELECT state FROM test_contract_record WHERE tenant_id=? AND id=?",String.class,tenant,id)).isEqualTo("ACCEPTED");
      assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(updated_by) FROM test_contract_record WHERE tenant_id=? AND id=?",String.class,tenant,id)).isEqualTo(actor);
      assertThatThrownBy(()->tx.execute(status->runtime.execute(context,"ThirdContract",id,"submit",0,Map.of()))).hasMessage("BUSINESS_OBJECT_STATE_CONFLICT");
    }finally{jdbc.execute("DROP TABLE test_contract_record");}
  }
}
