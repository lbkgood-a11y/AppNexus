package com.appnexus.printing.application;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.DocumentPrintRequest;
import java.util.Map;
/** 统一场景提交协作端口，不建立平行打印队列，不解除原场景、数据契约或设备准入。 */
public interface PrintSubmission {
 /** @param c 可信主体 @param sceneId 当前租户场景 @param sourceId 已授权来源 @param data 固定契约事实 @param output PRINTER或PDF @param device 当前租户设备，可按模式缺省 @param copies 1至99份 @return 原统一任务 */
 Map<String,Object> submitSnapshot(PrintContext c,String sceneId,String sourceId,Object data,String output,String device,int copies);
 /** @param c 可信主体 @param id 本人已保存单据 @param input 固定输出选择 @return 原统一场景提交结果 */
 Map<String,Object> print(PrintContext c,String id,DocumentPrintRequest input);
}
