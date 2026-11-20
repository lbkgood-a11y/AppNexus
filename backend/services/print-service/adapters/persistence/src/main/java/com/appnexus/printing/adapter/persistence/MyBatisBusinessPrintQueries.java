package com.appnexus.printing.adapter.persistence;
import com.appnexus.printing.application.BusinessPrintQueries;
import java.util.*;
/** 业务打印固定查询适配，SQL不决定业务授权或建立平行输出队列。 */
public final class MyBatisBusinessPrintQueries implements BusinessPrintQueries {
 private final BusinessPrintingMapper mapper;
 /** @param mapper 原场景、渲染事实与历史固定SQL */
 public MyBatisBusinessPrintQueries(BusinessPrintingMapper mapper){this.mapper=mapper;}
 public List<Map<String,Object>> scenes(String tenant,String contract){return mapper.scenes(tenant,contract);}
 public Boolean rendererOnline(String tenant){return mapper.rendererOnline(tenant);}
 public List<Map<String,Object>> history(String tenant,String source,String actor){return mapper.history(tenant,source,actor);}
}
