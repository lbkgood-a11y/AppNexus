import { attachmentsApi } from "./attachments";
import { http } from "@/utils/http";

/** 品类表单复用原化验、附件授权与真实租户会话。 */
export const inboundEvidenceApi = {
  labMetrics: (id: string) => http.request<{code:string;unit:string}[]>("get",
    `/api/bulk-inbound-quality-orders/${encodeURIComponent(id)}:decide`),
  async attachmentOptions(taskId: string) {
    const signal=new AbortController().signal;
    const items: {id:string;fileName:string;mediaType:string}[]=[];
    let offset=0;
    for (;;) {
      const page=await attachmentsApi.list(`inbound-task:${taskId}`,signal,offset,100);
      items.push(...page.rows.filter(row=>row.status==="AVAILABLE")
        .map(({id,fileName,mediaType})=>({id,fileName,mediaType})));
      offset+=page.rows.length;
      if(!page.rows.length || offset>=page.total)return items;
    }
  }
};
