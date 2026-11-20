package com.appnexus.platform.organization.port.personnel;
import java.util.*;
/** 人员固定存取输出端口；仅允许平台持久化适配器实现，租户、授权及完整事务由调用用例约束。 */
public interface PersonnelIdentityQueries {
 
  List<Map<String,Object>> active( String tenant);
 
  int touch( String tenant, String person, String actor);
 
  List<Map<String,Object>> people( String tenant, String member);
 
  List<String> members( String tenant, String person);
}
