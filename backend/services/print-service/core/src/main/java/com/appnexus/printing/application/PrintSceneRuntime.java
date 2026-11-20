package com.appnexus.printing.application;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.JobDraft;
import java.util.*;
import java.util.function.*;
/** 场景到统一打印运行能力的协作端口，禁止另建任务队列或解除原任务准入。 */
public interface PrintSceneRuntime {
 /** @param c 可信主体 @param draft 固定场景和契约的任务草稿 @return 原统一运行任务 */
 Map<String,Object> submit(PrintContext c,JobDraft draft);
 /** 构建装配注册同一目录和验证器，不读取业务正文。 @param catalog 当前固定租户契约目录 @param validator 原受控JSON数据契约验证器 */
 void dataContracts(BiFunction<PrintContext,String,Map<String,Object>> catalog,BiConsumer<Object,Map<String,Object>> validator);
}
