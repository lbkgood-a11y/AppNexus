package com.appnexus.printing.application;
import com.appnexus.contracts.printing.PrintContext;
import java.util.Map;
/** 已授权输出附件与预览确认协作端口，不创建公开文件凭证。 */
public interface PrintPreviewOutput {
 /** @param c 可信当前主体 @param id 已授权原任务 @return 原受控PDF附件 */
 byte[] outputPdf(PrintContext c,String id);
 /** @param c 可信当前主体 @param id 精确预览任务 @param device 当前租户启用设备 @param copies 1至99份 @return 原预览确认生成的任务 */
 Map<String,Object> printPreview(PrintContext c,String id,String device,int copies);
}
