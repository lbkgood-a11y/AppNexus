<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import InboundNoticeWorkspace from "./inbound-notice-workspace.vue";
import InboundBatchWorkspace from "./inbound-batch-workspace.vue";
import { inboundBatchesApi, type BatchLine, type InboundBatchDocument } from "@/api/inboundBatches";
import { BusinessWorkspace, FloatingError, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import { returnTransitApi } from "@/api/returnTransit";
import { http, requestErrorMessage } from "@/utils/http";

type Row = Record<string, unknown>;
interface NoticeLine extends Row {
  lineNo: number; id: string; productId: string; identityKind: string; identityId: string; productVersion: number;
  unitId: string; unitName?: string; productName?: string; approvedQuantity: number; receivedQuantity: number;
}
interface Position {
  warehouseId: string; areaId: string; locationId: string;
  warehouseVersion: number; areaVersion: number; locationVersion: number;
}
interface NoticeCustody { id: string; lineId: string; arrivalBatchNo: string | null; position: Position; receiptId: string | null; entryId: string | null }
interface NoticeHead { noticeId: string; documentNo: string; warehouseId: string; warehouseName?: string; ownerId: string; ownerName?: string; status: string }
interface DocumentView {
  rows: Row[]; related: Row[]; coils?: Row[]; truncated?: boolean;
  batch?: InboundBatchDocument; notice?: NoticeHead; lines?: NoticeLine[]; custodies?: NoticeCustody[];
}

const kinds: Record<string, string> = {
  "inbound-notices": "INBOUND_NOTICE",
  "inbound-receipts": "INBOUND_RECEIPT",
  "inbound-titles": "INBOUND_TITLE",
  "inbound-quality": "INBOUND_QUALITY",
  "title-transfers": "TITLE_TRANSFER",
  "outbound-notices": "OUTBOUND_NOTICE",
  "outbound-trips": "OUTBOUND_TRIP",
  "outbound-confirmations": "OUTBOUND_CONFIRMATION",
  "handover-reversals": "HANDOVER_REVERSAL"
};
// 查询和签发互斥，应用壳属性始终落在当前真实根容器。
defineOptions({ inheritAttrs: false });
const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();
const key = computed(() => route.path.split("/").at(-1) ?? "");
const zh = computed(() => !locale.value.startsWith("en"));
const title = computed(() => {
  const labels = route.meta.title as Record<string, string> | undefined;
  return labels?.[zh.value ? "zh-CN" : "en"] ?? "";
});
/** 按固定页面使用原业务权限对应的操作，GET 服务端独立授权。 */
const readOperation = computed(() => {
  switch (key.value) {
    case "inbound-entries": return "BulkWarehouseStockBalance.read_entry";
    case "inbound-receipts": return "BulkWarehouseStockBalance.read_receipt_document";
    case "pickup-authorizations": return "BulkCoilOutbound.read_authorization_document";
    case "return-dispatches": return "BulkCoilOutbound.read_return_transit";
    case "return-confirmations": return "BulkCoilOutbound.read_return_document";
    case "stock-restrictions": return "BulkStockRestriction.read_document";
    default: return "BulkWarehouseStockBalance.read_document";
  }
});
const creating = ref(false);
const batchMode = ref<"entry" | "receipt">();
const batchLabel = (key: string) => t("inboundBatch." + key);
const batchSource = computed(() => result.value?.batch ?? result.value?.notice);
const batchLines = computed<BatchLine[]>(() => {
  if (batchMode.value === "receipt") return result.value?.batch?.lines.filter(row => !row.processed) ?? [];
  return (result.value?.lines ?? []).filter(row => row.hasCapacity === true)
    .map(row => ({ ...row, noticeLineId: row.id }));
});
const allowReceipt = computed(() => key.value === "inbound-entries" && result.value?.batch?.lines.some(row => !row.processed));
const pathsForBatch = { ENTRY: "/warehousing/inbound-entries", RECEIPT_DOCUMENT: "/warehousing/inbound-receipts" };
async function openSavedBatch(id: string) {
  const destination = batchMode.value === "entry" ? pathsForBatch.ENTRY : pathsForBatch.RECEIPT_DOCUMENT;
  batchMode.value = undefined;
  await router.push({ path: destination, query: { number: id, batch: "1" } });
}
const action = ref<{mode:"custody" | "receive"; noticeId:string; ownerId:string; warehouseId:string;
  line:NoticeLine; custody?:NoticeCustody}>();
const inbound = (value: string) => t("inboundNotice." + value);
const workingNotice = computed(() => ["ISSUED","RECEIVING"].includes(result.value?.notice?.status ?? ""));
const custodyRows = computed(() => result.value?.custodies?.map(custody => ({
  id:custody.id,lineId:custody.lineId,arrivalBatchNo:custody.arrivalBatchNo,locationId:custody.position.locationId,
  receiptId:custody.receiptId,entryId:custody.entryId
})) ?? []);
function processLine(lineId:unknown,custodyId?:unknown) {
  const view=result.value;
  const notice=view?.notice;
  const line=view?.lines?.find(item=>item.id===lineId);
  const custody=view?.custodies?.find(item=>item.id===custodyId);
  if (!notice || !line || (custodyId && (!custody || custody.receiptId || custody.lineId!==lineId))) return;
  action.value={mode:custody ? "receive" : "custody",noticeId:notice.noticeId,
    warehouseId:notice.warehouseId,ownerId:notice.ownerId,line,custody};
}
const number = ref("");
const result = ref<DocumentView>();
const loading = ref(false);
const error = ref("");
let request = 0;

const labels = {
  id: ["单号", "Document number"], documentNo: ["单号", "Document number"],
  completedQuantity: ["已完成量", "Completed quantity"], vehicleNumber: ["车牌或车辆编号", "Vehicle number"],
  restoredQuota: ["恢复额度", "Restored quota"], restoredStock: ["恢复库存", "Restored stock"],
  createdBy: ["创建人", "Created by"], updatedAt: ["更新时间", "Updated at"], updatedBy: ["更新人", "Updated by"], parentId: ["来源标识", "Source ID"], kind: ["类型", "Type"],
  warehouseId: ["仓库", "Warehouse"], ownerId: ["货主", "Owner"],
  productId: ["商品", "Product"], identityId: ["品规", "Item"],
  otherOwnerId: ["转入货主标识", "Receiving owner ID"], quantity: ["数量", "Quantity"],
  receiptId: ["实收库存标识", "Receipt stock ID"], transitId: ["返仓发运标识", "Return dispatch ID"],
  evidenceId: ["凭证标识", "Evidence ID"], releaseEvidenceId: ["解除凭证标识", "Release evidence ID"],
  serial: ["卷号", "Coil number"], receiptBatchNo: ["实收批次", "Receipt batch"],
  unitId: ["计量单位", "Unit"], createdAt: ["创建时间", "Created at"],
  noticeId: ["通知标识", "Notice ID"], authorizationId: ["授权标识", "Authorization ID"],
  unassigned: ["未派车预留", "Unassigned reserve"], qualityEvidenceId: ["质检放行凭证", "Quality release evidence"],
  cap: ["上限", "Limit"], reserved: ["已占用", "Reserved"], issued: ["已出", "Issued"], remaining: ["剩余", "Remaining"],
  state: ["状态", "State"], status: ["状态", "Status"], receivingWarehouseId: ["实际接收仓标识", "Receiving warehouse ID"],
  sourceWarehouseId: ["来源仓标识", "Source warehouse ID"], previousWarehouseId: ["原接收仓标识", "Previous warehouse ID"],
  handoverId: ["原交接标识", "Original handover ID"], receivedReturnId: ["返仓确认标识", "Return confirmation ID"],
  measuredQuantity: ["实测量", "Measured quantity"], bookQuantity: ["账面量", "Book quantity"], reason: ["原因", "Reason"]
} as const;
function label(field: string) {
  if (["noticeId","entryId","carrier","arrivedAt","measurementRef","measurementId","custodyId","expectedQuantity","rejectionQuantity","rejectionReason","processed","stockStatus"].includes(field)) return batchLabel(field);
  if (["lineNo","lineId","identityKind","approvedQuantity","receivedQuantity","plannedQuantity","arrivalBatchNo","remark",
    "productVersion","flowVersion","locationId","operations","issuedAt","expectedArrivalAt"].includes(field)) return inbound(field);
  if (field === "otherOwnerId" && key.value === "inbound-titles") return zh.value ? "登记货主" : "Registered owner";
  if (field === "otherOwnerId" && key.value.startsWith("outbound-")) return zh.value ? "提货方" : "Pickup partner";
  if (field === "otherOwnerId" && key.value === "handover-reversals") return zh.value ? "提货方" : "Pickup partner";
  return labels[field as keyof typeof labels]?.[zh.value ? 0 : 1] ?? field;
}

/** 显示列使用服务端给出的授权名称，原标识继续留给查询与提交。 */
const referenceColumns: Record<string,string> = {
  warehouseId: "warehouseName", ownerId: "ownerName", otherOwnerId: "otherOwnerName",
  productId: "productName", identityId: "identityName", unitId: "unitName", areaId: "areaName", locationId: "locationName",
  receivingWarehouseId: "receivingWarehouseName", previousWarehouseId: "previousWarehouseName",
  sourceWarehouseId: "sourceWarehouseName", createdBy: "createdByName", updatedBy: "updatedByName"
};
const resolvedFields = new Set(Object.values(referenceColumns));
function columns(rows: Row[] | undefined): ManagementColumn[] {
  return Object.keys(rows?.[0] ?? { documentNo: "" }).filter(field => !resolvedFields.has(field)).map(field => ({
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

function formatKind(kind: string) {
  const inbound = { CUSTODY: ["接管记录", "Custody"], RECEIPT: ["实收记录", "Receipt"] } as const;
  const special = inbound[kind as keyof typeof inbound];
  if (special) return special[zh.value ? 0 : 1];
  return key.value === "return-dispatches"
    ? t("returnTransit.actions." + kind.toLowerCase(), kind) : t("coilFlow.kinds." + kind, kind);
}

/** 来源发运下按原逐卷凭证展示改仓、短损及接管，不跨发运拼接记录。 */
async function loadDispatch(id: string): Promise<DocumentView> {
  const found = await returnTransitApi.load(id);
  return {
    rows: [{ documentNo: found.id, handoverId: found.handoverId, sourceWarehouseId: found.sourceWarehouseId,
      receivingWarehouseId: found.receivingWarehouseId, receivingWarehouseName: found.receivingWarehouseName, sourceWarehouseName: found.sourceWarehouseName, evidenceId: found.evidenceId }],
    coils: found.coils.map(coil => ({
      receiptId: coil.receiptId, serial: coil.serial, state: coil.state, bookQuantity: coil.bookQuantity,
      measuredQuantity: coil.measuredQuantity, unitId: found.unitId, unitName: found.unitName, receivingWarehouseId: coil.receivingWarehouseId, receivingWarehouseName: coil.receivingWarehouseName,
      receivedReturnId: coil.receivedReturnId
    })),
    related: found.history.map(event => ({
      kind: event.kind, id: event.id, receiptId: event.receiptId, evidenceId: event.evidenceId,
      previousWarehouseId: event.previousWarehouseId, previousWarehouseName: event.previousWarehouseName, warehouseId: event.warehouseId, warehouseName: event.warehouseName, reason: event.reason
    }))
  };
}

/** 主单固定字段和明细快照集中映射；服务端已核对来源通知和品规。 */
function batchView(page: string, batch: InboundBatchDocument): DocumentView {
  const common = { documentNo: batch.id, noticeId: batch.noticeId, warehouseId: batch.warehouseId, warehouseName: batch.warehouseName, ownerId: batch.ownerId, ownerName: batch.ownerName };
  const stage = page === "inbound-entries"
    ? { vehicleNumber: batch.vehicleNumber, carrier: batch.carrier, arrivedAt: batch.arrivedAt }
    : { entryId: batch.entryId, measurementRef: batch.measurementRef };
  return { rows: [{ ...common, ...stage, remark: batch.remark, ...batch.audit }], batch, related: [] };
}
const batchRows = computed<Row[]>(() => {
  const lines = result.value?.batch?.lines ?? [];
  return lines.map(row => {
    const common = { lineNo: row.lineNo, productId: row.productId, productName: row.productName, identityId: row.identityId, identityName: row.identityName, unitId: row.unitId, unitName: row.unitName };
    return key.value === "inbound-entries"
      ? { ...common, locationId: row.position?.locationId, locationName: row.position?.locationName, expectedQuantity: row.expectedQuantity,
        evidenceId: row.evidenceId, processed: row.processed }
      : { ...common, custodyId: row.custodyId, receivedQuantity: row.receivedQuantity,
        rejectionQuantity: row.rejectionQuantity, rejectionReason: row.rejectionReason,
        measurementId: row.measurementId, receiptId: row.receiptId, stockStatus: row.stockStatus };
  });
});

/** 各入口按自身业务单号查主表。没有实收、配货或接管关联时，仍可显示已存在的主单。 */
async function load(page: string, id: string): Promise<DocumentView> {
  const encoded = encodeURIComponent(id);
  if (page === "inbound-entries") return batchView(page, await inboundBatchesApi.entry(id));
  if (page === "inbound-receipts" && route.query.legacy !== "1") {
    try { return batchView(page, await inboundBatchesApi.receipt(id)); }
    catch (cause) { if ((cause as { response?: { status?: number } })?.response?.status !== 404) throw cause; }
  }
  switch (page) {
    case "return-dispatches":
      return loadDispatch(id);
    case "return-confirmations": {
      const found = await http.request<{ header: Row; lines: Row[] }>("get", "/api/bulk-coil-return-confirmations/" + encoded);
      return { rows: [found.header], related: found.lines };
    }
    case "pickup-authorizations": {
      const row = await http.request<Row>("get", "/api/bulk-pickup-authorizations/" + encoded + "/balance");
      return { rows: [row], related: [] };
    }
    case "stock-restrictions": {
      const row = await http.request<Row>("get", "/api/bulk-stock-restrictions/" + encoded);
      return { rows: [row], related: [] };
    }
    default: {
      const found = await http.request<{ header: Row & { audit: Row } & NoticeHead; related: Row[];
        relatedTruncated: boolean; lines:NoticeLine[]; custodies:NoticeCustody[] }>(
        "get", "/api/bulk-warehouse-documents/" + kinds[page] + "/" + encoded);
      const { audit, ...header } = found.header;
      if (page === "inbound-notices") {
        return { rows:[{ documentNo:header.documentNo,warehouseId:header.warehouseId,ownerId:header.ownerId,warehouseName:header.warehouseName,ownerName:header.ownerName,
          status:header.status,flowVersion:header.flowVersion,issuedAt:header.issuedAt,
          expectedArrivalAt:header.expectedArrivalAt,remark:header.remark,...audit }], notice:found.header, lines:found.lines,
          custodies:found.custodies, related:found.related, truncated:found.relatedTruncated };
      }
      const { noticeId: _noticeId, status: _status, flowVersion: _flowVersion, issuedAt: _issuedAt,
        expectedArrivalAt: _expectedArrivalAt, remark: _remark, ...other } = header;
      return { rows: [{ ...other, ...audit }], related: found.related, truncated: found.relatedTruncated };
    }
  }
}

/** 新查询和路由变更丢弃过期结果，避免前一张单据残留在另一个权限页面。 */
async function search() {
  const current = ++request;
  const documentNo = key.value === "inbound-notices" ? number.value.trim() : number.value.trim().toLowerCase();
  error.value = "";
  result.value = undefined;
  loading.value = false;
  if (key.value === "inbound-notices" ? !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(documentNo) : !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(documentNo)) {
    error.value = key.value === "inbound-notices" ? inbound("requiredNumber") : zh.value ? "请输入有效的单号（UUID）。" : "Enter a valid document number (UUID).";
    return;
  }
  loading.value = true;
  try {
    const found = await load(key.value, documentNo);
    if (current === request) result.value = found;
  } catch (cause) {
    if (current === request) error.value = requestErrorMessage(cause);
  } finally {
    if (current === request) loading.value = false;
  }
}

/** 签发确认后关闭表单，并由同页查询参数触发权威单据读取。 */
function showIssuedNotice(noticeId: string) {
  creating.value = false;
  action.value = undefined;
  if (route.query.number === noticeId) {
    number.value = noticeId;
    void search();
    return;
  }
  void router.replace({ query: { ...route.query, number: noticeId } });
}

watch(() => route.fullPath, () => {
  batchMode.value = undefined;
  creating.value = false;
  action.value = undefined;
  request++;
  number.value = typeof route.query.number === "string" ? route.query.number : "";
  result.value = undefined;
  loading.value = false;
  error.value = "";
  if (number.value) void search();
}, { immediate: true });
</script>

<template>
  <InboundBatchWorkspace v-if="batchMode && batchSource" v-bind="$attrs" :mode="batchMode"
    :notice-id="batchSource.noticeId" :warehouse-id="batchSource.warehouseId"
    :entry-id="result?.batch?.id" :lines="batchLines" @saved="openSavedBatch" @cancel="batchMode = undefined" />
  <InboundNoticeWorkspace v-else-if="creating || action" v-bind="$attrs" :action="action"
    @saved="showIssuedNotice" @cancel="creating = false; action = undefined" />
  <div v-else v-bind="$attrs" class="management-page">
    <BusinessWorkspace :title="title">
      <template #search>
        <el-form inline @submit.prevent="search()">
          <div class="management-filter">
            <el-form-item :label="label('documentNo')">
              <el-input v-model.trim="number"  :maxlength="key === 'inbound-notices' ? 64 : 36" clearable />
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-if="key === 'inbound-notices'" v-operation="'BulkInboundReceipt.issue'" type="primary"
              @click="creating = true">{{ t("coilFlow.createNotice") }}</el-button>
            <el-button v-if="workingNotice" v-operation="'BulkInboundReceipt.record_entry'"
              type="primary" @click="batchMode = 'entry'">{{ batchLabel("newEntry") }}</el-button>
            <el-button v-if="allowReceipt" v-operation="'BulkInboundReceipt.confirm_document'"
              type="primary" @click="batchMode = 'receipt'">{{ batchLabel("newReceipt") }}</el-button>
            <router-link v-if="key === 'inbound-entries' && !result?.batch" :to="{ path: '/warehousing/inbound-notices' }">
              {{ batchLabel("fromNotice") }}
            </router-link>
            <router-link v-if="key === 'inbound-receipts' && !result?.batch" :to="{ path: pathsForBatch.ENTRY }">
              {{ batchLabel("fromEntry") }}
            </router-link>
            <el-button v-operation="readOperation" type="primary" native-type="submit" :loading="loading">
              {{ t("masterData.search") }}
            </el-button>
          </div>
        </el-form>
      </template>
      <template #feedback><FloatingError :title="error" /></template>
      <template #data>
        <div class="business-workspace-stack management-table-stack">
          <ManagementTable embedded :title="zh ? '查询结果' : 'Search results'" :columns="columns(result?.rows)"
            :rows="displayRows(result?.rows ?? [])" :loading="loading" :empty-text="t('masterData.empty')" @refresh="search()">
            <template #noticeId="{ row }">
              <router-link v-if="typeof row.noticeId === 'string'" :to="{ path: '/warehousing/inbound-notices',query:{number:row.noticeId} }">
                {{ row.noticeId }}
              </router-link>
            </template>
            <template #entryId="{ row }">
              <router-link v-if="typeof row.entryId === 'string'" :to="{ path: pathsForBatch.ENTRY,query:{number:row.entryId} }">
                {{ row.entryId }}
              </router-link>
            </template>
            <template #status="{ row }">{{ key === "inbound-notices" ? inbound("states." + row.status)
              : t("warehouseTitle.states." + row.status, String(row.status)) }}</template>
          </ManagementTable>
          <ManagementTable v-if="batchRows.length" embedded :title="batchLabel('lines')"
            :columns="columns(batchRows)" :rows="displayRows(batchRows)" @refresh="search()">
            <template #processed="{ row }">{{ batchLabel(row.processed ? 'received' : 'pending') }}</template>
            <template #stockStatus="{ row }">{{ batchLabel('stockStates.' + String(row.stockStatus)) }}</template>
          </ManagementTable>
          <ManagementTable v-if="result?.lines?.length" embedded :title="inbound('lines')"
            :columns="columns(result.lines.map(line => ({
              lineNo:line.lineNo,productId:line.productId,identityKind:line.identityKind,identityId:line.identityId,
              unitId:line.unitId,plannedQuantity:line.plannedQuantity,approvedQuantity:line.approvedQuantity,
              receivedQuantity:line.receivedQuantity,remark:line.remark
            })))" :rows="displayRows(result.lines)" @refresh="search()" />
          <ManagementTable v-if="custodyRows.length" embedded :title="inbound('custodies')"
            :columns="columns(custodyRows.map(row=>({...row,operations:''})))" :rows="displayRows(custodyRows)" @refresh="search()">
            <template #entryId="{ row }">
              <router-link v-if="typeof row.entryId === 'string'" :to="{ path: pathsForBatch.ENTRY, query: { number: row.entryId } }">
                {{ row.entryId }}
              </router-link>
            </template>
            <template #operations="{ row }">
              <el-button v-if="workingNotice && !row.receiptId && !row.entryId" v-operation="'BulkInboundReceipt.receive'"
                link type="primary" @click="processLine(row.lineId,row.id)">{{ inbound("receive") }}</el-button>
            </template>
          </ManagementTable>
          <ManagementTable v-if="result?.coils?.length" embedded :title="t('returnTransit.coils')"
            :columns="columns(result.coils)" :rows="displayRows(result.coils)" @refresh="search()">
            <template #state="{ row }">{{ t("returnTransit.states." + row.state, String(row.state)) }}</template>
          </ManagementTable>
          <ManagementTable v-if="result?.related.length" embedded :title="zh ? '关联记录' : 'Related records'"
            :columns="columns(result.related)" :rows="displayRows(result.related)" @refresh="search()">
            <template #kind="{ row }">{{ row.kind === "ENTRY" ? batchLabel("entry")
              : row.kind === "RECEIPT_DOCUMENT" ? batchLabel("receipt") : formatKind(String(row.kind)) }}</template>
            <template #id="{ row }">
              <router-link v-if="typeof row.id === 'string' && (row.kind === 'ENTRY' || row.kind === 'RECEIPT_DOCUMENT')"
                :to="{ path: pathsForBatch[row.kind],query:{number:row.id,batch:'1'} }">{{ row.id }}</router-link>
              <template v-else>{{ row.id }}</template>
            </template>
            <template #transitId="{ row }">
              <router-link v-if="typeof row.transitId === 'string'" :to="{ path: '/warehousing/return-dispatches', query: { number: row.transitId } }">
                {{ row.transitId }}
              </router-link>
            </template>
          </ManagementTable>
          <el-alert v-if="result?.truncated" type="info" :closable="false"
            :title="zh ? '此处显示前 100 条关联记录。' : 'Showing the first 100 related records.'" />
        </div>
      </template>
    </BusinessWorkspace>
  </div>
</template>
