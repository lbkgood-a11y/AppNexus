import { http } from "@/utils/http";
import type { WarehouseTitleApi } from "@appnexus/warehousing";
import { masterDataApi } from "./masterData";
import { productV1Api } from "./productV1";

/** 仓储工作区沿用主数据有权选项、宿主会话和写入 CSRF。 */
export const warehouseTitleApi: WarehouseTitleApi = {
  operations: () => masterDataApi.operations(),
  owners: async keyword => {
    const page = await masterDataApi.list("partner", { keyword, enabled: true, page: 1, size: 30 });
    return page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
  },
  warehouses: async keyword => {
    const page = await masterDataApi.list("warehouse", { keyword, enabled: true, page: 1, size: 30 });
    return page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
  },
  products: async name => {
    const page = await productV1Api.list({ name, code: "", archived: false, page: 1, size: 30 });
    return page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
  },
  identities: async id => {
    const structure = await productV1Api.structure(id);
    return [
      ...structure.specifications.map(row => ({ id: row.id, label: row.name })),
      ...structure.skus.map(row => ({ id: row.id, label: row.code }))
    ];
  },
  units: async () => {
    const page = await http.request<{ rows: { id: string; name: string }[] }>(
      "get", "/api/master-data/product-unit", { params: { page: 1, size: 100 } }
    );
    return page.rows.map(row => ({ id: row.id, label: row.name }));
  },
  physical: scope => http.request("get", "/api/bulk-warehouse-stock-balances", { params: scope }),
  coils: (scope, afterId) => http.request("get", "/api/bulk-warehouse-stock-balances/coils", { params: { ...scope, afterId } }),
  authorizations: (scope, afterId) => http.request("get", "/api/bulk-pickup-authorization-balances", { params: { ...scope, afterId } }),
  recoveryEntries:(authorizationId,afterId)=>http.request("get",
    `/api/bulk-pickup-authorizations/${encodeURIComponent(authorizationId)}/recovery-entries`,{params:{afterId}}),
  transfer: async data => {
    const csrf = await http.request<{ headerName: string; token: string }>("get", "/bff/csrf");
    return http.request("post", "/api/bulk-coil-title-transfers", { data, headers: { [csrf.headerName]: csrf.token } });
  }
};
