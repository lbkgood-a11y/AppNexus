package com.appnexus.printing.adapter.jdbc;

import com.appnexus.printing.application.PrintReceiptStore;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** 回执存储适配器；固定租户条件及行锁，数据库判断租约，JSON 结构比较不依赖对象键顺序。 */
public final class MyBatisPrintReceiptStore implements PrintReceiptStore {
  private final PrintReceiptMapper statements;
  private final ObjectMapper mapper;
  /** @param statements 与完整回执事务共用连接的固定Mapper @param mapper 与既有 PrintJson 相同 JSON 实现，不改变历史快照协议 */
  public MyBatisPrintReceiptStore(PrintReceiptMapper statements,ObjectMapper mapper){this.statements=statements;this.mapper=mapper;}
  /** {@inheritDoc} */
  @Override public Current lock(String tenant,String id,String result){
    var rows=statements.lock(tenant,id);if(rows.isEmpty())throw new PrintFailure("PRINT_JOB_NOT_FOUND",404);
    var row=rows.getFirst();Object expired=row.get("expired");
    boolean ended=Boolean.TRUE.equals(expired)||expired instanceof Number value&&value.intValue()!=0;
    Object previous=null;String text=(String)row.get("resultJson");
    if(text!=null)try{previous=mapper.readTree(text);}catch(Exception ignored){/* 损坏结果不能当作相同回执。 */}
    return new Current((String)row.get("state"),(String)row.get("claimed_by"),ended,Objects.equals(previous,mapper.readTree(result)));
  }
  /** {@inheritDoc} */
  @Override public void update(String tenant,String id,String state,String result){
    statements.update(tenant,id,state,result);
  }
}
