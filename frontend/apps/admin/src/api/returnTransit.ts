import type { ReturnPreparation, ReturnTransitApi, TransitAction } from "@appnexus/warehousing/return-transit-types";
import { http } from "@/utils/http";
import { coilFlowApi } from "./coilFlow";
import { warehouseTitleApi } from "./warehouseTitle";

const base="/api/bulk-coil-return-transits";
const routes:Record<TransitAction,string>={
  freeze:"/api/bulk-stock-restrictions",
  lift:"/api/bulk-stock-restrictions:lift",
  quality:"/api/bulk-coil-returns:release-quality",
  receive:"/api/bulk-coil-returns:post-coils",
  loss:base+":settle-loss",
  reopen:base+":reopen-loss",
  reroute:base+":reroute-coil"
};

/** 在途操作使用固定业务地址和当前宿主 CSRF，不在页面存储安全凭证。 */
async function post<T>(path:string,data:object):Promise<T> {
  const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
  return http.request("post",path,{data,headers:{[csrf.headerName]:csrf.token}});
}

export const returnTransitApi:ReturnTransitApi={
  operations:warehouseTitleApi.operations,
  owners:warehouseTitleApi.owners,
  locations:coilFlowApi.locations,
  units:warehouseTitleApi.units,
  restrictions:id=>http.request("get","/api/bulk-stock-restrictions/by-receipt/"+encodeURIComponent(id)),
  load:id=>http.request("get",base+"/"+encodeURIComponent(id)),
  prepare:id=>http.request<ReturnPreparation>("get",base+"/eligible-coils/"+encodeURIComponent(id)),
  dispatch:input=>post(base,input),
  submit:(action,input)=>post(routes[action],input),
  trace:coilFlowApi.trace
};
