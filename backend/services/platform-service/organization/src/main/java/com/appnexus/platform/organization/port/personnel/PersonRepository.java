package com.appnexus.platform.organization.port.personnel;
import com.appnexus.platform.organization.PersonData;
import java.util.*;
import com.appnexus.platform.organization.PersonData.Input;
/** 人员固定存取输出端口；仅允许平台持久化适配器实现，租户、授权及完整事务由调用用例约束。 */
public interface PersonRepository {
 
 
 List<Map<String,Object>> list( String tenant, String keyword, Boolean enabled, int size, int offset);
 
  long count( String tenant, String keyword, Boolean enabled);
 
  int create( String tenant, String id, Input input, Map<String,String> values, String actor);
 
  int update( String tenant, String id, Input input, Map<String,String> values);
 
  int status( String tenant, String id, boolean enabled, long version);
 
  List<Map<String,String>> previous( String tenant, String id);
 
  int audit( String tenant, String id, String actor);
}
