<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import InboundNoticeWorkspace from "./inbound-notice-workspace.vue";
import { AppPagination, PageHeader, TabbedDetailTemplate, RecordAuditDetails, BusinessWorkspace, FloatingError, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import { coilFlowApi } from "@/api/coilFlow";
import { masterDataApi } from "@/api/masterData";
import { http, requestErrorMessage } from "@/utils/http";

type Row = Record<string, unknown>;
interface NoticeLine extends Row {
  id: string; productId: string; identityKind: string; identityId: string; productVersion: number;
  audit?: Row;
  unitId: string; unitName?: string; productName?: string; approvedQuantity: number; receivedQuantity: number;
}
interface DocumentView {
  rows: Row[];
  lines: NoticeLine[];
}

// 查询和签发互斥，应用壳属性始终落在当前真实根容器。
defineOptions({ inheritAttrs: false });
const route = useRoute();
const router = useRouter();

const { t, locale } = useI18n();
const zh = computed(() => !locale.value.startsWith("en"));
const title = computed(() => {
  const labels = route.meta.title as Record<string, string> | undefined;
  return labels?.[zh.value ? "zh-CN" : "en"] ?? "";
});
const readOperation = computed(() => noticeList.value
  ? "BulkWarehouseStockBalance.list_inbound_notices" : "BulkWarehouseStockBalance.read_document");
const creating = ref(false);
const editingId = ref<string>();
const inbound = (value: string) => t("inboundNotice." + value);
const number = ref("");
const result = ref<DocumentView>();
const loading = ref(false);
const error = ref("");
let request = 0;
const selectedNotice = ref<string>();
const noticeList = computed(() => !selectedNotice.value);
const warehouseFilter = ref("");
const ownerFilter = ref("");
const upstreamFilter = ref("");
const warehouses = ref<{ id: string; label: string }[]>([]);
const owners = ref<Awaited<ReturnType<typeof coilFlowApi.owners>>>([]);
const noticePage = ref<{ rows: (Row & { audit?: Row })[]; total: number }>({ rows: [], total: 0 });
const currentPage = ref(1);
const pageSize = ref(20);
let warehouseRequest = 0;
let ownerRequest = 0;
type NoticeFilters = { documentNo?: string; warehouseId?: string; ownerId?: string; appointmentNo?: string };
let appliedFilters: NoticeFilters = {};
const noticeColumns = computed<ManagementColumn[]>(() => [
  ...columns([noticeFields]),
  { key: "operations", prop: "operations", label: inbound("operations"), width: 160, fixed: "right" }
]);
const noticeFields = {
  status: "", documentNo: "", warehouseId: "", ownerId: "", appointmentNo: "", plannedConveyance: "",
  issuedAt: "", expectedArrivalAt: "", flowVersion: "", remark: ""
};

/** 只从当前有权主数据读取筛选候选，保留仍被选中的值。 */
async function searchNoticeWarehouses(keyword = "") {
  const current = ++warehouseRequest;
  try {
    const page = await masterDataApi.list("warehouse", { keyword, enabled: true, page: 1, size: 100 });
    if (current !== warehouseRequest) return;
    const options = page.rows.map(row => ({ id: row.id, label: `${row.code} / ${row.name}` }));
    warehouses.value = [...options, ...warehouses.value.filter(row => row.id === warehouseFilter.value && !options.some(item => item.id === row.id))];
  } catch (cause) { if (current === warehouseRequest) error.value = requestErrorMessage(cause); }
}

async function searchNoticeOwners(keyword = "") {
  const current = ++ownerRequest;
  try {
    const options = await coilFlowApi.owners(keyword);
    if (current !== ownerRequest) return;
    owners.value = [...options, ...owners.value.filter(row => row.id === ownerFilter.value && !options.some(item => item.id === row.id))];
  } catch (cause) { if (current === ownerRequest) error.value = requestErrorMessage(cause); }
}

/** 按已提交条件读取授权页及真实总数，查询失败保留当前结果。 */
async function loadNoticePage(filters: NoticeFilters, requestedPage = 1, requestedSize = pageSize.value) {
  const current = ++request;
  loading.value = true;
  error.value = "";
  try {
    const read = (page: number) => http.request<typeof noticePage.value>("get", "/api/bulk-inbound-notices",
      { params: { ...filters, page, pageSize: requestedSize } });
    let page = await read(requestedPage);
    if (current !== request) return;
    const lastPage = Math.max(1, Math.ceil(page.total / requestedSize));
    const resolvedPage = Math.min(requestedPage, lastPage);
    if (resolvedPage !== requestedPage) page = await read(resolvedPage);
    if (current !== request) return;
    noticePage.value = page;
    currentPage.value = resolvedPage;
    pageSize.value = requestedSize;
    appliedFilters = filters;
  } catch (cause) {
    if (current === request) error.value = requestErrorMessage(cause);
  } finally {
    if (current === request) loading.value = false;
  }
}

/** 页容量变化及新筛选均从第一页读取，普通跳页沿用已提交的条件。 */
function pageNotices(page: number, size: number) {
  if (loading.value || (page === currentPage.value && size === pageSize.value)) return;
  void loadNoticePage(appliedFilters, size === pageSize.value ? page : 1, size);
}

const labels = {
  createdBy: ["创建人", "Created by"], createdAt: ["创建时间", "Created at"],
  updatedBy: ["更新人", "Updated by"], updatedAt: ["更新时间", "Updated at"],
  warehouseId: ["仓库", "Warehouse"], ownerId: ["货主", "Owner"],
  productId: ["商品", "Product"], identityId: ["品规", "Item"],
  unitId: ["计量单位", "Unit"], status: ["状态", "Status"]
} as const;
/** 明细只展示业务字段，内部标识及容量判断不作为列。 */
const detailLineColumns = computed<ManagementColumn[]>(() => [
  {key:"productId",prop:"productName",label:label("productId"),minWidth:160},
  {key:"identityId",prop:"identityName",label:label("identityId"),minWidth:130},
  {key:"unitId",prop:"unitName",label:label("unitId"),width:100},
  ...["plannedQuantity","approvedQuantity","receivedQuantity","contractNo","remark"].map(key=>({
    key,prop:key,label:inbound(key),minWidth:120
  }))
]);

/** 沿用应用当前语言的日期时间格式，空值显示统一占位。 */
function detailValue(field: string,value: unknown) {
  if(value==null || value==="")return "—";
  if(["issuedAt","expectedArrivalAt"].includes(field)){
    const date=new Date(String(value));
    return Number.isNaN(date.getTime()) ? "—" : new Intl.DateTimeFormat(locale.value,{
      dateStyle:"medium",timeStyle:"medium"
    }).format(date);
  }
  return value;
}

function label(field: string) {
  if (field === "documentNo") return inbound("noticeNo");
  if (field === "appointmentNo") return zh.value ? "业务单号" : "Upstream document number";
  if (["lineId","identityKind","approvedQuantity","receivedQuantity","plannedQuantity","arrivalBatchNo","remark",
    "productVersion","flowVersion","locationId","operations","issuedAt","expectedArrivalAt","appointmentNo","plannedConveyance","contractNo"].includes(field)) return inbound(field);
  return labels[field as keyof typeof labels]?.[zh.value ? 0 : 1] ?? field;
}

/** 显示列使用服务端给出的授权名称，原标识继续留给查询与提交。 */
const referenceColumns: Record<string,string> = {
  warehouseId: "warehouseName", ownerId: "ownerName", productId: "productName",
  identityId: "identityName", unitId: "unitName", createdBy: "createdByName", updatedBy: "updatedByName"
};
const resolvedFields = new Set(Object.values(referenceColumns));
/** 单据结果按接口实际返回字段展示，优先呈现业务状态和单号。 */
function columns(rows: Row[] | undefined): ManagementColumn[] {
  const fields = Object.keys(rows?.[0] ?? { documentNo: "" }).filter(field => field !== "audit" && !resolvedFields.has(field));
  const priority = [fields.includes("status") ? "status" : "state", fields.includes("documentNo") ? "documentNo" : "id"]
    .filter(field => fields.includes(field));
  return [...priority, ...fields.filter(field => !priority.includes(field))].map(field => ({
    key: field, prop: referenceColumns[field] ?? field, label: label(field),
    minWidth: field === "id" || field === "documentNo" ? 230 : 115
  }));
}
function displayRows(rows: readonly object[] = []): Row[] {
  return rows.map(source => {
    const row = {...source} as Row;
    for (const [id, name] of Object.entries(referenceColumns)) {
      if (id in row) row[name] ||= row[id] ? t("referenceName.unavailable") : "—";
    }
    return row;
  });
}


/** 按通知单号读取已授权的通知及明细。 */
async function load(documentNo: string): Promise<DocumentView> {
  const found = await http.request<{ header: Row & { audit: Row }; lines: NoticeLine[] }>(
    "get", "/api/bulk-warehouse-documents/INBOUND_NOTICE/" + encodeURIComponent(documentNo));
  const { audit, ...header } = found.header;
  const fields = Object.keys(noticeFields).filter(field => !["createdBy", "createdAt", "updatedBy", "updatedAt"].includes(field));
  const row = Object.fromEntries(fields.map(field => [field, header[field]]));
  return { rows: [{ ...row, warehouseName: header.warehouseName, ownerName: header.ownerName, ...audit }], lines: found.lines };
}

/** 新查询和路由变更丢弃过期结果，避免前一张单据残留在另一个权限页面。 */
async function search() {
  if (noticeList.value) {
    await loadNoticePage({ documentNo: number.value || undefined, warehouseId: warehouseFilter.value || undefined,
      ownerId: ownerFilter.value || undefined, appointmentNo: upstreamFilter.value || undefined });
    return;
  }
  const current = ++request;
  const documentNo = number.value.trim();
  error.value = "";
  result.value = undefined;
  loading.value = false;
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(documentNo)) {
    error.value = inbound("requiredNumber");
    return;
  }
  loading.value = true;
  try {
    const found = await load(documentNo);
    if (current === request) result.value = found;
  } catch (cause) {
    if (current === request) error.value = requestErrorMessage(cause);
  } finally {
    if (current === request) loading.value = false;
  }
}

/** 清空本地查单结果与地址里的单号，取消尚未返回的旧查询。 */
function resetDocumentQuery() {
  request++;
  number.value = "";
  warehouseFilter.value = "";
  ownerFilter.value = "";
  upstreamFilter.value = "";
  appliedFilters = {};
  result.value = undefined;
  loading.value = false;
  error.value = "";
  if (noticeList.value) void loadNoticePage({});
}

/** 与客商查询页一致，查看与返回切换本页视图，不创建带单号的新页签。 */
function openNotice(id: string) {
  selectedNotice.value = id;
  number.value = id;
}

/** 兼容旧的通知定位链接，返回后清除定位参数并保留筛选与页码。 */
function backToList() {
  request++;
  selectedNotice.value = undefined;
  result.value = undefined;
  number.value = appliedFilters.documentNo ?? "";
  if (typeof route.query.number === "string") {
    void router.replace({ query: { ...route.query, number: undefined }, hash: route.hash });
    return;
  }
  void loadNoticePage(appliedFilters, currentPage.value);
}

/** 保存成功后通过原授权查询重新读取详情。 */
function showIssuedNotice(noticeId: string) {
  creating.value = false;
  editingId.value = undefined;
  openNotice(noticeId);
}

watch(() => route.fullPath, () => {
  creating.value = false;
  editingId.value = undefined;
  request++;
  selectedNotice.value = typeof route.query.number === "string" ? route.query.number : undefined;
  number.value = selectedNotice.value ?? "";
  result.value = undefined;
  loading.value = false;
  error.value = "";
  if (noticeList.value) {
    number.value = appliedFilters.documentNo ?? "";
    void loadNoticePage(appliedFilters, currentPage.value);
    if (!warehouses.value.length) void searchNoticeWarehouses();
    if (!owners.value.length) void searchNoticeOwners();
  }
}, { immediate: true });
</script>

<template>
  <InboundNoticeWorkspace v-if="creating || editingId" v-bind="$attrs" :id="editingId" @saved="showIssuedNotice" @cancel="creating = false; editingId = undefined" />
  <InboundNoticeWorkspace v-else-if="!noticeList" :key="selectedNotice" v-bind="$attrs"
    :id="selectedNotice" readonly @cancel="backToList" />
  <BusinessWorkspace v-else v-bind="$attrs" :title="title">
      <template #search>
        <el-form inline @submit.prevent="search()">
          <el-form-item :label="label('documentNo')">
            <el-input v-model.trim="number" maxlength="64" clearable />
          </el-form-item>
          <template v-if="noticeList">
            <el-form-item :label="label('warehouseId')">
              <el-select v-model="warehouseFilter" filterable remote :remote-method="searchNoticeWarehouses" clearable style="width: 180px">
                <el-option v-for="row in warehouses" :key="row.id" :value="row.id" :label="row.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="label('ownerId')">
              <el-select v-model="ownerFilter" filterable remote :remote-method="searchNoticeOwners" clearable style="width: 180px">
                <el-option v-for="row in owners" :key="row.id" :value="row.id" :label="row.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="label('appointmentNo')">
              <el-input v-model.trim="upstreamFilter" maxlength="64" clearable />
            </el-form-item>
          </template>
          <el-form-item class="management-filter-actions">
            <el-button v-operation="readOperation" type="primary" native-type="submit" :loading="loading">
              {{ t("masterData.search") }}
            </el-button>
            <el-button :disabled="loading" @click="resetDocumentQuery">{{ t("masterData.reset") }}</el-button>
          </el-form-item>
        </el-form>
      </template>
      <template #data>
        <!-- 列表直接作为数据区子项，继承客商列表的视口填满和底部分页布局。 -->
        <FloatingError :title="error" />
          <ManagementTable v-if="noticeList" embedded :title="title"
            :total="noticePage.total" :columns="noticeColumns"
            :rows="displayRows(noticePage.rows)" :loading="loading" :empty-text="t('masterData.empty')" @refresh="loadNoticePage(appliedFilters, currentPage)">
            <template #actions>
              <el-button v-operation="'BulkInboundReceipt.issue'" type="primary"
                @click="creating = true">{{ t("coilFlow.createNotice") }}</el-button>
            </template>
            <template #documentNo="{ row }">
              <el-button v-operation="'BulkWarehouseStockBalance.read_document'" link type="primary" @click="openNotice(String(row.noticeId))">{{ row.documentNo }}</el-button>
            </template>
            <template #operations="{ row }">
              <el-button v-operation="'BulkWarehouseStockBalance.read_document'" link type="primary"
                :disabled="loading" @click="openNotice(String(row.noticeId))">
                {{ t("partner.view") }}
              </el-button>
              <el-button v-operation="'BulkInboundReceipt.update'" link type="primary" :disabled="loading"
                @click="editingId = String(row.noticeId)">{{ t("masterData.edit") }}</el-button>
            </template>
            <template #pagination>
              <AppPagination :current-page="currentPage" :page-size="pageSize" :total="noticePage.total"
                :disabled="loading" layout="total, sizes, prev, pager, next, jumper" @update:current-page="page => pageNotices(page, pageSize)"
                @update:page-size="size => pageNotices(1, size)" />
            </template>
            <template #issuedAt="{ row }">{{ detailValue('issuedAt',row.issuedAt) }}</template>
            <template #expectedArrivalAt="{ row }">{{ detailValue('expectedArrivalAt',row.expectedArrivalAt) }}</template>
            <template #status="{ row }"><el-tag class="app-status-tag" type="info">{{ inbound("states." + row.status) }}</el-tag></template>
          </ManagementTable>

      </template>
  </BusinessWorkspace>
</template>
