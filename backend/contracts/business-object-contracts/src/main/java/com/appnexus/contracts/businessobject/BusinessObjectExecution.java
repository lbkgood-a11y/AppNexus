package com.appnexus.contracts.businessobject;

import java.util.Map;

/** 对象操作的公共执行入口；不接受客户端提供业务事实或指定流程、模板及解析引用。 */
public interface BusinessObjectExecution {
  /**
   * 从唯一契约读取记录、解析关联配置并执行能力；失败必须回滚解析及本地能力写入。
   * @param context 认证链建立的可信身份和稳定请求键
   * @param request 对象、操作及读取版本，不包含业务数据
   * @return 能力输出及实际关联；类型化命令由原用例执行，无流程关联
   */
  Result execute(BusinessObjects.Context context, Request request);

  /**
   * @param context 由平台管理入口验证的账号上下文，tenantId 必须为空
   * @param request 仅允许平台操作登记的类型化命令
   * @return 经原平台管理权限和业务用例处理的结果
   */
  default Result executePlatform(BusinessObjects.Context context,Request request){
    throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
  }

  /**
   * 已声明对象操作；打印使用 PREVIEW，设备及确认由输出任务管理。
   * @param objectType 唯一对象类型
   * @param objectId 已持久化记录标识；类型化命令可按声明使用配置键或省略
   * @param operationCode 对象操作编码
   * @param operationId 真实页面入口或已声明别名
   * @param basisVersion 非负依据版本；类型化写入必须与命令版本一致
   * @param command 可选类型化命令 JSON 对象；格式由本次固定操作定义，历史记录能力调用不传
   */
  record Request(String objectType,String objectId,String operationCode,String operationId,long basisVersion,Map<String,Object> command) {
    /** 保留原记录能力调用方式。 */
    public Request(String objectType,String objectId,String operationCode,String operationId,long basisVersion){
      this(objectType,objectId,operationCode,operationId,basisVersion,null);
    }
    public Request {if(command!=null)command=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(command));}
    /** 调试输出只包含对象与操作编码，命令可能含联系方式或账户资料。 */
    @Override public String toString(){return "Request[objectType="+objectType+", operationCode="+operationCode+"]";}
  }
  /** 关联结果及专业能力返回值；不把解析标识作为授权凭证。 */
  record Result(BusinessObjects.Resolution resolution,Map<String,Object> output) {
    /** 专业能力的可选状态字段允许null，不能因打印尚无设备或回执而回滚已创建任务。 */
    public Result {output=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(output));}
  }
}
