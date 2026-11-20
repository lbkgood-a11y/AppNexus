package com.appnexus.platform.businessobject.port;

import com.appnexus.contracts.businessobject.*;
import java.util.Map;

/** 只执行对象所有者编译映射的存储端口；不能接收SQL、URL或管理员自定义列。 */
public interface ObjectRecordStore {
  /** 通过可信租户与对象ID查询，lock=true在当前事务持有行锁；不存在统一拒绝。 */
  Map<String,Object> read(ObjectRecordMapping mapping,BusinessObjects.Context context,String id,boolean lock);
  /** 写入受控状态转换；必须以原版本、实例及状态为条件，并在失败时回滚审计与回执。 */
  void transition(ObjectRecordMapping mapping,BusinessObjects.Context context,String id,long version,String from,String to,String invocation,String instance);
}
