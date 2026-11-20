package com.appnexus.printing.api;
import java.util.*;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.*;
/** 打印公开用例；HTTP入口仅依赖此契约，不访问存储实现。 */
public interface PrintOutputManagement {
 /**
  * 进程内业务打印使用原统一输出路径，不建立第二套队列。
  * @param c 可信业务打印主体，由服务端构建
  * @param input 已授权业务事实与原数据契约，沿用原业务打印请求协议
  * @return 原统一输出受理结果
  */
 Map<String,Object> business(PrintContext c,com.appnexus.contracts.printing.BusinessPrinting.Request input);

 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<TargetCheck> preflight(PrintContext c,String documentId,String mode,String deviceId);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> external(PrintContext c,ExternalOutputRequest input);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> document(PrintContext c,String id,DocumentOutputRequest input);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> status(PrintContext c,String id);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 byte[] pdf(PrintContext c,String id);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> confirm(PrintContext c,String id,String device);
}
