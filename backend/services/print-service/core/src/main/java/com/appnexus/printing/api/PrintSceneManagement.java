package com.appnexus.printing.api;
import java.util.*;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.*;
/** 打印公开用例；HTTP入口仅依赖此契约，不访问存储实现。 */
public interface PrintSceneManagement {
 /** 场景目录沿用原三种任选功能权限。 */
 Set<String> SCENE_READ_PERMISSIONS=Set.of("print.template:read","print.job:submit","print.template:publish");
 /** 契约目录沿用原设计、发布、提交及只读准入；外部调用仍走独立安全入口。 */
 Set<String> CONTRACT_READ_PERMISSIONS=Set.of("print.template:design","print.job:submit",
     "print.template:publish","print.external:submit","print.template:read");
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
