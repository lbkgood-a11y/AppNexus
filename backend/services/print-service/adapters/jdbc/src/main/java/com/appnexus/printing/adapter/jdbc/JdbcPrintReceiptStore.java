package com.appnexus.printing.adapter.jdbc;

import com.appnexus.printing.application.PrintReceiptStore;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** 回执存储适配器；固定租户条件及行锁，数据库判断租约，JSON 结构比较不依赖对象键顺序。 */
public final class JdbcPrintReceiptStore implements PrintReceiptStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  /** @param jdbc 与完整回执事务共用连接 @param mapper 与既有 PrintJson 相同 JSON 实现，不改变历史快照协议 */
  public JdbcPrintReceiptStore(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
  /** {@inheritDoc} */
  @Override public Current lock(String tenant,String id,String result){
    return jdbc.query("SELECT state,claimed_by,result_json,(claim_expires_at<CURRENT_TIMESTAMP(6)) expired FROM print_job WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",rs->{
      if(!rs.next())throw new PrintFailure("PRINT_JOB_NOT_FOUND",404);
      Object expired=rs.getObject("expired");
      boolean ended=Boolean.TRUE.equals(expired)||expired instanceof Number value&&value.intValue()!=0;
      Object previous=null;String text=rs.getString("result_json");
      if(text!=null)try{previous=mapper.readTree(text);}catch(Exception ignored){/* 与原快照投影一致：损坏结果不视为相同回执。 */}
      return new Current(rs.getString("state"),rs.getString("claimed_by"),ended,Objects.equals(previous,mapper.readTree(result)));
    },tenant,id);
  }
  /** {@inheritDoc} */
  @Override public void update(String tenant,String id,String state,String result){
    jdbc.update("UPDATE print_job SET state=?,result_json=CAST(? AS JSON),claim_expires_at=NULL WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",state,result,tenant,id);
  }
}
