import type { PositionOption, ProductOptions } from "@appnexus/warehousing/flow-types";
import { http } from "@/utils/http";
import { masterDataApi } from "./masterData";
import { productV1Api } from "./productV1";
import { warehouseTitleApi } from "./warehouseTitle";

/** 写入使用宿主安全会话中的 CSRF 凭证。 */
async function post<T>(path:string,data:object):Promise<T> {
  const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
  return http.request("post",path,{data,headers:{[csrf.headerName]:csrf.token}});
}

/** 固定真实仓储命令；主数据作用域及 CSRF 与既有工作区共用。 */
export const coilFlowApi={
  owners:warehouseTitleApi.owners,
  products:warehouseTitleApi.products,
  locations:async(kind: "warehouse"): Promise<PositionOption[]> =>{
    const page=await masterDataApi.list(kind,{keyword:"",enabled:true,page:1,size:100});
    return page.rows.map(row=>({id:row.id,code:row.code,version:row.version,label:`${row.code} / ${row.name}`}));
  },
  product:async (id: string): Promise<ProductOptions> =>{
    const [product,structure]=await Promise.all([productV1Api.read(id),productV1Api.structure(id)]);
    if (!product.unitId) throw new Error("COIL_FLOW_UNIT_REQUIRED");
    const effectiveSkus=structure.skus.filter(row=>row.effective);
    const neededUnits=new Set([product.unitId,...effectiveSkus.map(row=>row.measurement?.inventoryUnitId ?? product.unitId)]);
    const units=new Map<string,{id:string;code:string;name:string}>();
    let currentPage=1;
    while (neededUnits.size) {
      const page=await http.request<{rows:{id:string;code:string;name:string;enabled:boolean}[];total:number}>(
      "get","/api/master-data/product-unit",{params:{page:currentPage,size:100}});
      for (const row of page.rows) {
        if (neededUnits.delete(row.id) && row.enabled !== false) units.set(row.id,row);
      }
      if (page.rows.length<100 || currentPage*100>=page.total) break;
      currentPage++;
    }
    const unit=units.get(product.unitId);
    if (!unit) throw new Error("COIL_FLOW_UNIT_REQUIRED");
    const items:ProductOptions["items"]=[
      ...structure.specifications.filter(row=>row.enabled&&!row.retiredAt).map(row=>({
        id:row.id,label:row.name,kind:"SPECIFICATION" as const
      })),
      ...effectiveSkus.map(row=>{
        const configured=units.get(row.measurement?.inventoryUnitId ?? unit.id);
        return {id:row.id,label:row.code,kind:"SKU" as const,specificationId:row.specificationId,
          inventoryUnit:configured ? {id:configured.id,label:configured.name} : undefined};
      })
    ];
    if (structure.mode==="NONE") items.unshift({id,label:product.name,kind:"PRODUCT"});
    return {unitId:unit.id,unitCode:unit.code,unitName:unit.name,version:structure.version,items};
  },
  issue: (data: object) => post("/api/bulk-inbound-receipts:issue", data)
};
