package com.appnexus.printing.api;
import java.util.*;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.*;
/** 打印公开用例；HTTP入口仅依赖此契约，不访问存储实现。 */
public interface PrintRuntimeManagement {
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,String> agentIdentity(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> templates(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> saveTemplate(PrintContext c, String id, TemplateDraft d);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> template(PrintContext c,String id);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> publish(PrintContext c,String id,long revision);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> versions(PrintContext c,String id);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> restoreVersion(PrintContext c,String id,int version,long revision);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> devices(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> saveDevice(PrintContext c,String id,DeviceDraft d);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 List<Map<String,Object>> jobs(PrintContext c);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> reprint(PrintContext c,String id,String reason);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Optional<Map<String,Object>> claim(PrintContext c,String site,String agent);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> receipt(PrintContext c,String id,Receipt receipt);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 Map<String,Object> uploadPdf(PrintContext c,String id,String agent,byte[] bytes);
 /** 打印公开操作；保持真实主体、功能权限、数据契约及原事务约束。 */
 byte[] downloadPdf(PrintContext c,String id);
}
