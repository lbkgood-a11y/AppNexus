package com.appnexus.contracts.printing;

import java.util.*;

/** 业务打印公开端口；数据必须由业务授权投影提供，打印层再次校验租户、场景及契约。 */
public interface BusinessPrinting {
  /** 当前租户两分钟内是否上报过队列，仅是瞬时可用提示，不保证后续任务必然成功。 */
  default boolean rendererOnline(PrintContext context){return false;}
  /** 仅在业务先授权对象、打印层验证任务读取权后返回该来源的输出摘要，不返回快照原文。 */
  default List<Map<String,Object>> history(PrintContext context,String sourceId){return List.of();}
  /** 场景目录只包含兼容且启用的真实发布模板，不返回业务数据。 */
  record Scene(String id,String code,String name,String dataContract) {}
  /** 授权业务快照；mode 为 PREVIEW/SILENT，份数为 1—99，场景编码不可由模板 ID 代替。 */
  record Request(String sceneCode,String dataContract,String sourceId,Map<String,Object> data,String mode,String deviceId,int copies) {}
  /** 当前租户与指定契约兼容的打印场景。 */
  List<Scene> scenes(PrintContext context,String dataContract);
  /** 创建可查询、可幂等重放的打印输出；HTTP 超时不能更换请求键盲目重试。 */
  Map<String,Object> request(PrintContext context,Request request);
}
