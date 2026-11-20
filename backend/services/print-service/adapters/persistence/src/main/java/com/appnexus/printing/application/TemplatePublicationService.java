package com.appnexus.printing.application;

import com.appnexus.contracts.printing.*;
import java.util.Map;
import java.util.UUID;

/** 模板发布应用用例；固定草稿版本后校验契约、追加历史并推进指针，不自动提升设计人员权限。 */
public final class TemplatePublicationService {
  private final TemplatePublicationPort port;
  private final PrintPlatform platform;
  /** @param port 同一数据源事务中的发布输出端口 @param platform 每次发布重新复核的权限端口 */
  public TemplatePublicationService(TemplatePublicationPort port,PrintPlatform platform){this.port=port;this.platform=platform;}
  /**
   * 原子发布模板；由事务适配入口调用，禁止绕过事务直接提交版本与指针。
   * @param context 可信租户及实际操作者
   * @param id 模板 UUID，先授权后检查格式
   * @param revision 读取的草稿版本；不一致返回冲突，不发布新草稿
   * @return 发布后的原模板响应；不读取真实业务数据
   */
  public Map<String,Object> publish(PrintContext context,String id,long revision){
    if(!platform.allowed(context,"print.template:publish"))throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    try{UUID.fromString(id);}catch(Exception error){throw new PrintRejection("PRINT_IDENTIFIER_INVALID",400);}
    var draft=port.lock(context,id);
    if(draft.revision()!=revision)throw new PrintRejection("PRINT_TEMPLATE_CONFLICT",409);
    var schema=port.validatedSchema(context,id,draft.contract());
    int version=draft.published()==null?1:draft.published()+1;
    port.publish(context,id,version,schema);
    return port.result(context,id);
  }
}
