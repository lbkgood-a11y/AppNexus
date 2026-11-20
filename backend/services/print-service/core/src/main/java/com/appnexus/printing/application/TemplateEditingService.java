package com.appnexus.printing.application;

import com.appnexus.contracts.printing.*;
import java.util.Map;
import java.util.UUID;

/** 模板草稿编辑协调；先设计权限，再验证内容与标识，CAS 写入不隐式发布。 */
public final class TemplateEditingService {
  private final TemplateEditingPort port;
  private final PrintPlatform platform;
  /** @param port 原编辑事务中的 JSON 及固定持久化输出 @param platform 可信实时权限端口 */
  public TemplateEditingService(TemplateEditingPort port,PrintPlatform platform){this.port=port;this.platform=platform;}
  /** @param context 可信租户及账号 @param id 新建时 null，编辑时目标 UUID @param draft 完整草稿字段；原版本冲突拒绝覆盖 @return 原读取权限下的草稿；事务适配器包裹写入及结果查询 */
  public Map<String,Object> save(PrintContext context,String id,TemplateEditingPort.Draft draft){
    if(!platform.allowed(context,"print.template:design"))throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    if(draft==null||!com.appnexus.printing.core.TemplateDraftRules.valid(draft.code(),draft.contract(),draft.name(),draft.category(),draft.revision()))throw new PrintRejection("PRINT_INPUT_INVALID",400);
    port.prepare();
    port.identifier(context.actorId());port.identifier(context.tenantId());
    if(id==null){id=UUID.randomUUID().toString();port.create(context,id,draft);}
    else{port.identifier(id);if(port.update(context,id,draft)==0)throw new PrintRejection("PRINT_TEMPLATE_CONFLICT",409);}
    return port.result(context,id);
  }
}
