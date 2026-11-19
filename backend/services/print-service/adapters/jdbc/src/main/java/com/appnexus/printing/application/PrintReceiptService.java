package com.appnexus.printing.application;

import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.contracts.printing.PrintPlatform;
import com.appnexus.contracts.printing.PrintRejection;
import com.appnexus.printing.core.PrintJobLifecycle;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** 代理回执应用用例；协调授权、身份、幂等与领域状态，不引用 JDBC、JSON 或 Spring。 */
public final class PrintReceiptService {
  private final PrintReceiptStore store;
  private final PrintPlatform platform;
  /** @param store 参加调用方完整事务的回执存储 @param platform 每次执行重新复核的可信打印权限端口 */
  public PrintReceiptService(PrintReceiptStore store, PrintPlatform platform) { this.store=store;this.platform=platform; }
  /**
   * 接收非敏感代理回执；由事务入站装饰器包裹，幂等重放也必须校验原代理和结果一致性。
   * @param context 服务端可信租户与账号，不接受正文覆盖
   * @param id 任务 UUID，先授权再验证格式
   * @param state 允许的目标状态，null 按原错误码拒绝
   * @param agent 领取代理标识，必填且最多128字符
   * @param result 延迟编解码端口回调；仅在授权与参数检查后读取，必须验证非敏感 JSON，不允许读取数据库或产生写入
   * @throws PrintRejection 未授权、格式不合法、代理不匹配或不允许转换时拒绝
   */
  public void accept(PrintContext context, String id, String state, String agent, Supplier<String> result) {
    if(!platform.allowed(context,"print.agent:operate")) fail("PRINT_ACCESS_DENIED",403);
    try{UUID.fromString(id);}catch(Exception error){fail("PRINT_IDENTIFIER_INVALID",400);}
    if(!PrintJobLifecycle.validReceipt(state)) fail("PRINT_RECEIPT_STATE_INVALID",400);
    if(agent==null||agent.isBlank()||agent.length()>128) fail("PRINT_INPUT_INVALID",400);
    String encoded=result.get();
    var current=store.lock(context.tenantId(),id,encoded);
    if(!Objects.equals(agent,current.agent())) fail("PRINT_JOB_RECEIPT_CONFLICT",409);
    if(current.state().equals(state)&&current.sameResult())return;
    if(!PrintJobLifecycle.permitsReceipt(current.state(),state,current.expired()))fail("PRINT_JOB_RECEIPT_CONFLICT",409);
    store.update(context.tenantId(),id,state,encoded);
  }
  private static void fail(String code,int status){throw new PrintRejection(code,status);}
}
