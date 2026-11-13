package com.appnexus.printing.adapter.jdbc;

import com.appnexus.contracts.printing.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 公开打印桥接只复用现有场景与输出编排，不建立第二套队列。 */
public final class BusinessPrintingAdapter implements BusinessPrinting {
  private final JdbcTemplate jdbc;private final PrintOutputs outputs;private final PrintPlatform platform;
  public BusinessPrintingAdapter(JdbcTemplate jdbc,PrintOutputs outputs,PrintPlatform platform){this.jdbc=jdbc;this.outputs=outputs;this.platform=platform;}
  @Override public List<Scene> scenes(PrintContext c,String contract){
    if(!platform.allowed(c,"print.template:read")&&!platform.allowed(c,"print.job:submit"))throw new PrintFailure("PRINT_ACCESS_DENIED",403);
    return jdbc.query("SELECT BIN_TO_UUID(s.id) id,s.code,s.name,s.data_contract FROM print_scene s JOIN print_template t ON t.tenant_id=s.tenant_id AND t.id=s.template_id JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE s.tenant_id=UUID_TO_BIN(?) AND s.enabled=TRUE AND t.enabled=TRUE AND s.data_contract=? AND v.data_contract=s.data_contract",
      (r,n)->new Scene(r.getString("id"),r.getString("code"),r.getString("name"),r.getString("data_contract")),c.tenantId(),contract);
  }
  @Override public Map<String,Object> request(PrintContext c,Request input){return outputs.business(c,input);}
  @Override public boolean rendererOnline(PrintContext c){
    if(!platform.allowed(c,"print.job:submit"))throw new PrintFailure("PRINT_ACCESS_DENIED",403);
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(?) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))",Boolean.class,c.tenantId()));
  }
  @Override public List<Map<String,Object>> history(PrintContext c,String source){
    if(!platform.allowed(c,"print.job:read"))throw new PrintFailure("PRINT_ACCESS_DENIED",403);
    return jdbc.queryForList("SELECT BIN_TO_UUID(r.id) id,r.created_at, j.state, BIN_TO_UUID(r.confirmed_job_id) confirmedJobId, delivery.state deliveryState, delivery.updated_at deliveryUpdatedAt FROM print_output_request r JOIN print_job j ON j.tenant_id=r.tenant_id AND j.id=r.job_id LEFT JOIN print_job delivery ON delivery.tenant_id=r.tenant_id AND delivery.id=r.confirmed_job_id WHERE r.tenant_id=UUID_TO_BIN(?) AND j.source_id=? AND r.owner=? ORDER BY r.created_at LIMIT 100",c.tenantId(),source,c.actorId());
  }
}
