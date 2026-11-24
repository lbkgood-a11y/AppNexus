import { masterDataApi } from "./masterData";
import { productV1Api } from "./productV1";

/** 使用主数据的授权选项供入仓通知与库存总览查询。 */
export const warehouseTitleApi = {
  owners: async (keyword: string) => {
    const page = await masterDataApi.list("partner", { keyword, enabled: true, page: 1, size: 30 });
    return page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
  },
  warehouses: async (keyword: string) => {
    const page = await masterDataApi.list("warehouse", { keyword, enabled: true, page: 1, size: 30 });
    return page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
  },
  products: async (name: string) => {
    const page = await productV1Api.list({ name, code: "", archived: false, page: 1, size: 30 });
    return page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
  }
};
