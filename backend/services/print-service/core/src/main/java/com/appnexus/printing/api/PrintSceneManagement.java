package com.appnexus.printing.api;
import java.util.*;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.*;
/** 打印公开用例；HTTP入口仅依赖此契约，不访问存储实现。 */
public interface PrintSceneManagement {
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> scenes(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> saveScene(PrintContext c,String id,SceneRequest d);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> documents(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> saveDocument(PrintContext c,DocumentRequest d);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> print(PrintContext c,String id,DocumentPrintRequest d);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> contracts(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> createContract(PrintContext c,Object definitionValue);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 void report(PrintContext c,QueueReport d);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> queues(PrintContext c);
}
