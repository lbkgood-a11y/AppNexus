package com.appnexus.printing.adapter.persistence;
import com.appnexus.printing.application.BusinessPrintQueries;
import java.util.*;
/** 业务打印固定查询适配，SQL不决定业务授权或建立平行输出队列。 */
public final class MyBatisBusinessPrintQueries implements BusinessPrintQueries {
 private final BusinessPrintingMapper mapper;
 /** @param mapper 原场景、渲染事实与历史固定SQL */
 public MyBatisBusinessPrintQueries(BusinessPrintingMapper mapper){this.mapper=mapper;}
 public List<Map<String,Object>> scenes(Object tenant,Object contract){return mapper.scenes(tenant,contract);}
 public Boolean rendererOnline(Object tenant){return mapper.rendererOnline(tenant);}
 public List<Map<String,Object>> history(Object tenant,Object source,Object actor){return mapper.history(tenant,source,actor);}
}
