package com.appnexus.contracts.businessobject;

import java.util.Map;

/** 公共执行器的专业能力输出端口；目标及数据均来自已校验的对象契约和固定关联。 */
public interface CapabilityDriver {
  /** @return 本驱动器实现的唯一能力类型 */
  InteractionContract.Service service();

  /**
   * 执行已解析能力；不能由浏览器直接构造此调用。
   * @param context 服务端可信身份，保留被代表人员与组织
   * @param capability 同一对象操作的能力声明
   * @param resolution 服务端按当前业务事实解析的固定目标
   * @param snapshot 对象授权后重读的不可变投影，字段严格符合能力契约
   * @return 能力真实输出，不伪造成功状态
   */
  Map<String,Object> execute(BusinessObjects.Context context,InteractionContract.Capability capability,
      BusinessObjects.Resolution resolution,BusinessObjectRuntime.Snapshot snapshot);
}
