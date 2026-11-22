import type { CoilFlowApi, FlowStep } from "@appnexus/warehousing/flow-types";
import { http } from "@/utils/http";
import { masterDataApi } from "./masterData";
import { productV1Api } from "./productV1";
import { warehouseTitleApi } from "./warehouseTitle";

const routes:Record<FlowStep,string>={
  inbound:"/api/bulk-inbound-receipts:issue",
  custody:"/api/bulk-inbound-receipts:take-custody",
  receive:"/api/bulk-inbound-receipts",
  title:"/api/bulk-inbound-title-registrations",
  quality:"/api/bulk-inbound-quality-releases",
  identify:"/api/bulk-stock-coils",
  transfer:"/api/bulk-coil-title-transfers",
  outbound:"/api/bulk-outbound-notices",
  authorize:"/api/bulk-pickup-authorizations",
  trip:"/api/bulk-outbound-trips",
  allocate:"/api/bulk-coil-allocations",
  vehicle:"/api/bulk-outbound-trips:verify-vehicle-limits",
  weigh:"/api/bulk-outbound-trips:review-weighing",
  handover:"/api/bulk-outbound-trips:handover",
  return:"/api/bulk-coil-returns:post-coils"
};

/** 写入使用宿主安全会话中的 CSRF 凭证。 */
async function post<T>(path:string,data:object):Promise<T> {
  const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
  return http.request("post",path,{data,headers:{[csrf.headerName]:csrf.token}});
}

/** 固定真实仓储命令；主数据作用域及 CSRF 与既有工作区共用。 */
export const coilFlowApi:CoilFlowApi={
  operations:warehouseTitleApi.operations,
  owners:warehouseTitleApi.owners,
  products:warehouseTitleApi.products,
  locations:async(kind,scope)=>{
    const page=await masterDataApi.list(kind,{keyword:"",enabled:true,scope,page:1,size:100});
    return page.rows.map(row=>({id:row.id,code:row.code,version:row.version,label:`${row.code} / ${row.name}`}));
  },
  product:async id=>{
    const [product,structure]=await Promise.all([productV1Api.read(id),productV1Api.structure(id)]);
    if (!product.unitId) throw new Error("COIL_FLOW_UNIT_REQUIRED");
    const page=await http.request<{rows:{id:string;code:string;name:string}[]}>(
      "get","/api/master-data/product-unit",{params:{page:1,size:100}});
    const unit=page.rows.find(row=>row.id===product.unitId);
    if (!unit) throw new Error("COIL_FLOW_UNIT_REQUIRED");
    const items:Awaited<ReturnType<CoilFlowApi["product"]>>["items"]=[
      ...structure.specifications.filter(row=>row.enabled&&!row.retiredAt).map(row=>({
        id:row.id,label:row.name,kind:"SPECIFICATION" as const
      })),
      ...structure.skus.filter(row=>row.effective).map(row=>({id:row.id,label:row.code,kind:"SKU" as const}))
    ];
    if (structure.mode==="NONE") items.unshift({id,label:product.name,kind:"PRODUCT"});
    return {unitId:unit.id,unitCode:unit.code,unitName:unit.name,version:structure.version,items};
  },
  submit:(step,data)=>post(routes[step],data),
  recovery:(authorizationId,receiptId)=>http.request("get",
    `/api/bulk-pickup-authorizations/${encodeURIComponent(authorizationId)}/coil-recovery`,{params:{receiptId}}),
  cancelAllocation:input=>post("/api/bulk-coil-allocations:cancel",input),
  eligibility:id=>http.request("get",`/api/bulk-outbound-stock/${encodeURIComponent(id)}/eligibility`),
  trace:id=>http.request("get",`/api/bulk-warehouse-stock-balances/coils/${encodeURIComponent(id)}/trace`),
  authorization:async(scope,id)=>{
    let afterId:string|undefined;
    do {
      const page=await warehouseTitleApi.authorizations(scope,afterId);
      const found=page.rows.find(row=>row.authorizationId===id);
      if (found) return found;
      if (!page.nextAfterId || page.nextAfterId===afterId) return undefined;
      afterId=page.nextAfterId;
    } while(afterId);
    return undefined;
  }
};
