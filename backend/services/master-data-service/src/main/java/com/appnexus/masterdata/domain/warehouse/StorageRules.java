package com.appnexus.masterdata.domain.warehouse;

import java.util.*;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 库区、库位的纯字段约束；父子启用及不可移动事实由持久化关系端口在锁内验证。 */
public final class StorageRules {
  private StorageRules() {}
  private static final Set<String> AREA_TYPES=Set.of("RECEIVING","WORK","GOOD","DEFECTIVE","PACKAGING","VIRTUAL","FINISHED");
  /** 父级编码必填且不可跨租户；库容单位吨，缺省未知，允许非负数且最多18位整数、6位小数。 */
  public static void validate(Kind kind,Map<String,String> values){
    if(kind!=Kind.STORAGE_AREA&&kind!=Kind.STORAGE_LOCATION)return;
    if(!values.get("parentCode").matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();
    if(!values.get("capacity").matches("|[0-9]{1,18}(\\.[0-9]{1,6})?"))throw invalid();
    if(kind==Kind.STORAGE_AREA&&!values.get("areaType").isEmpty()&&!AREA_TYPES.contains(values.get("areaType")))throw invalid();
  }
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
