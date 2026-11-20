package com.appnexus.printing.application;
import com.appnexus.printing.api.PrintOutputManagement;


import com.appnexus.contracts.printing.*;
import java.util.*;


/** 公开打印桥接只复用现有场景与输出编排，不建立第二套队列。 */
public final class BusinessPrintingService implements BusinessPrinting {
  private final BusinessPrintQueries mapper;private final PrintOutputManagement outputs;private final PrintPlatform platform;
  public BusinessPrintingService(BusinessPrintQueries mapper,PrintOutputManagement outputs,PrintPlatform platform){this.mapper=mapper;this.outputs=outputs;this.platform=platform;}
  @Override public List<Scene> scenes(PrintContext c,String contract){
    if(!platform.allowed(c,"print.template:read")&&!platform.allowed(c,"print.job:submit"))throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    return mapper.scenes(c.tenantId(),contract).stream().map(row->new Scene((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),(String)row.get("data_contract"))).toList();
  }
  @Override public Map<String,Object> request(PrintContext c,Request input){return outputs.business(c,input);}
  @Override public boolean rendererOnline(PrintContext c){
    if(!platform.allowed(c,"print.job:submit"))throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    return Boolean.TRUE.equals(mapper.rendererOnline(c.tenantId()));
  }
  @Override public List<Map<String,Object>> history(PrintContext c,String source){
    if(!platform.allowed(c,"print.job:read"))throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    return mapper.history(c.tenantId(),source,c.actorId());
  }
}
